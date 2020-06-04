package uk.ac.ebi.biosamples.clearinghouse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.biosamples.PipelineResult;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Curation;
import uk.ac.ebi.biosamples.model.Sample;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClearninghouseCallable implements Callable<PipelineResult> {
    private static final Logger LOG = LoggerFactory.getLogger(ClearninghouseCallable.class);
    static final ConcurrentLinkedQueue<String> failedQueue = new ConcurrentLinkedQueue<>();

    private final Sample sample;
    private final BioSamplesClient bioSamplesClient;
    private final String domain;
    private final List<Map<String, String>> curations;

    public ClearninghouseCallable(final BioSamplesClient bioSamplesClient, final Sample sample, final String domain,
                                  final List<Map<String, String>> curations) {
        this.bioSamplesClient = bioSamplesClient;
        this.sample = sample;
        this.domain = domain;
        this.curations = curations;
    }

    @Override
    public PipelineResult call() {
        int appliedCurations = 0;
        boolean success = true;
        try {
            for (final Map<String, String> curationAsMap : curations) {
                String preAttrString = curationAsMap.get("attributePre");
                String preValString = curationAsMap.get("valuePre");
                String postAttrString = curationAsMap.get("attributePost");
                String postValString = curationAsMap.get("valuePost");

                for (final Attribute sampleAttribute : sample.getAttributes()) {
                    if (sampleAttribute.getType().equals(postAttrString) && sampleAttribute.getValue().equals(postValString)) {
                        //already curated, ignore current curation
                        break;
                    }
                }

                boolean curationAccepted = false;

                if (preValString == null || preValString.isEmpty()) {
                    if ("NotDefined".equals(postValString)) {
                        //todo shall we import this curation?
                    } else if (postValString != null && !postValString.isEmpty()) {
                        final Attribute attributePost = Attribute.build(postAttrString, postValString);
                        final Curation curation = Curation.build(null, attributePost);

                        LOG.info("New curation found {}, {}", sample.getAccession(), curation);
                        bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);

                        appliedCurations++;
                        curationAccepted = true;
                    }
                } else {
                    for (final Attribute sampleAttribute : sample.getAttributes()) {
                        if (sampleAttribute.getType().equals(preAttrString) && sampleAttribute.getValue().equals(preValString)) {
                            final Attribute attributePost = Attribute.build(postAttrString, postValString,
                                    sampleAttribute.getTag(), sampleAttribute.getIri(), sampleAttribute.getUnit());
                            final Curation curation = Curation.build(sampleAttribute, attributePost);

                            LOG.info("New curation found {}, {}", sample.getAccession(), curation);
                            bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);

                            appliedCurations++;
                            curationAccepted = true;
                            break;
                        }
                    }
                }

                if (!curationAccepted) {
                    LOG.info("No attribute-value matched with suggested curation: " +
                                    "accession: {}, attrPre: {}, valPre: {}, attrPost: {}, valPost: {}"
                            , sample.getAccession(), preAttrString, preValString, postAttrString, postValString);
                }
            }
        } catch (final Exception e) {
            success = false;
            failedQueue.add(sample.getAccession());
            LOG.error("Failed to add curation on sample: " + sample.getAccession(), e);
        }

        return new PipelineResult(sample.getAccession(), appliedCurations, success);
    }
}
