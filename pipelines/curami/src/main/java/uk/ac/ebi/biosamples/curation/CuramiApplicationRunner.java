package uk.ac.ebi.biosamples.curation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.Resource;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biosamples.PipelinesProperties;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.model.filter.Filter;
import uk.ac.ebi.biosamples.mongo.model.MongoCurationRule;
import uk.ac.ebi.biosamples.mongo.repo.MongoCurationRuleRepository;
import uk.ac.ebi.biosamples.utils.AdaptiveThreadPoolExecutor;
import uk.ac.ebi.biosamples.utils.ArgUtils;
import uk.ac.ebi.biosamples.utils.ThreadUtils;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class CuramiApplicationRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CuramiApplicationRunner.class);

    private final BioSamplesClient bioSamplesClient;
    private final PipelinesProperties pipelinesProperties;
    private final Map<String, String> curationRules;
    private final MongoCurationRuleRepository repository;

    public CuramiApplicationRunner(BioSamplesClient bioSamplesClient,
                                   PipelinesProperties pipelinesProperties,
                                   MongoCurationRuleRepository repository) {
        this.bioSamplesClient = bioSamplesClient;
        this.pipelinesProperties = pipelinesProperties;
        this.repository = repository;
        this.curationRules = new HashMap<>();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Collection<Filter> filters = ArgUtils.getDateFilters(args);
        Instant startTime = Instant.now();
        LOG.info("Pipeline started at {}", startTime);
        long curationCount = 0;
        long sampleCount = 0;

        loadCurationRulesFromFileToDb(getFileNameFromArgs(args));
//        loadInitialCurationRules();
        curationRules.putAll(loadCurationRulesToMemory());

        try (AdaptiveThreadPoolExecutor executorService = AdaptiveThreadPoolExecutor.create(100, 10000, true,
                pipelinesProperties.getThreadCount(), pipelinesProperties.getThreadCountMax())) {

            Map<String, Future<Integer>> futures = new HashMap<>();
            for (Resource<Sample> sampleResource : bioSamplesClient.fetchSampleResourceAll("", filters)) {
                LOG.trace("Handling {}", sampleResource);
                Sample sample = sampleResource.getContent();
                if (sample == null) {
                    throw new RuntimeException("Null sample found while traversing through all samples. " +
                            "This could be due to network error or data inconsistency");
                }

                Callable<Integer> task = new SampleCuramiCallable(
                        bioSamplesClient, sample, pipelinesProperties.getCurationDomain(), curationRules);
                sampleCount++;
                if (sampleCount % 500 == 0) {
                    LOG.info("Scheduled sample count {}", sampleCount);
                }
                futures.put(sample.getAccession(), executorService.submit(task));
            }

            LOG.info("waiting for futures");
            ThreadUtils.checkAndCallbackFutures(futures, 0, new CurationCountCallback(curationCount));
        } finally {
            Instant endTime = Instant.now();
            LOG.info("Total samples processed {}", sampleCount);
            LOG.info("Total curation objects added {}", curationCount);
            LOG.info("Pipeline finished at {}", endTime);
            LOG.info("Pipeline total running time {} seconds", Duration.between(startTime, endTime).getSeconds());
        }
    }

    private Map<String, String> loadCurationRulesToMemory() {
        List<MongoCurationRule> mongoCurationRules = repository.findAll();
        return mongoCurationRules.stream()
                .collect(Collectors.toMap(MongoCurationRule::getAttributePre, MongoCurationRule::getAttributePost));
    }

    /*private void loadInitialCurationRules() {
        InputStream in;
        try {
            ClassPathResource resource = new ClassPathResource("curation_rules.csv");
            in = resource.getInputStream();
        } catch (IOException e) {
            LOG.error("Could not find file in classpath", e);
            return;
        }
//        InputStream in = getClass().getClassLoader().getResourceAsStream("curation_rules.csv");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try (BufferedReader bf = new BufferedReader(reader)) {
            String line = bf.readLine();
            LOG.info("Reading file: {} with headers: {}", "curation_rules.csv", line);
            while ((line = bf.readLine()) != null) {
                String[] curationRule = line.split(",");
                MongoCurationRule mongoCurationRule = MongoCurationRule.build(curationRule[0].trim(), curationRule[1].trim());
                repository.save(mongoCurationRule);
            }
        } catch (IOException e) {
            LOG.error("Could not find file in {}", "resources", e);
        }
    }*/

    private void loadCurationRulesFromFileToDb(String filePath) {
        Reader reader;
        //read it from given filepath, else read it from classpath
        try {
            if (filePath == null || filePath.isEmpty()) {
                ClassPathResource resource = new ClassPathResource("curation_rules.csv");
                reader = new InputStreamReader(resource.getInputStream());
            } else {
                reader = new FileReader(filePath);
            }
        } catch (IOException e) {
            LOG.error("Could not find specified file in {} or classpath", filePath, e);
            return;
        }

        try (BufferedReader bf = new BufferedReader(reader)) {
            String line = bf.readLine();
            LOG.info("Reading file with headers: {}", line);
            while ((line = bf.readLine()) != null) {
                String[] curationRule = line.split(",");
                MongoCurationRule mongoCurationRule = MongoCurationRule.build(curationRule[0].trim(), curationRule[1].trim());
                repository.save(mongoCurationRule);
            }
        } catch (IOException e) {
            LOG.error("Could not find file in {} or classpath", filePath, e);
        }
    }

    private String getFileNameFromArgs(ApplicationArguments args) {
        String curationRulesFile = null;
        if (args.getOptionNames().contains("file")) {
            curationRulesFile = args.getOptionValues("file").get(0);
        }

        return curationRulesFile;
    }

    public class CurationCountCallback implements ThreadUtils.Callback<Integer> {
        private Long totalCount;

        CurationCountCallback(Long totalCount) {
            this.totalCount = totalCount;
        }

        public void call(Integer count) {
            totalCount = totalCount + count;
        }
    }

}
