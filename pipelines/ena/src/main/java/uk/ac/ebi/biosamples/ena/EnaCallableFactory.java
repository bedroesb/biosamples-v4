package uk.ac.ebi.biosamples.ena;

import org.springframework.stereotype.Service;

import uk.ac.ebi.biosamples.PipelinesProperties;
import uk.ac.ebi.biosamples.client.BioSamplesClient;

@Service
public class EnaCallableFactory {
    private final BioSamplesClient bioSamplesClient;
    private final EnaXmlEnhancer enaXmlEnhancer;
    private final EnaElementConverter enaElementConverter;
    private final EraProDao eraProDao;
    private final String domain;

    public EnaCallableFactory(BioSamplesClient bioSamplesClient, EnaXmlEnhancer enaXmlEnhancer,
                              EnaElementConverter enaElementConverter, EraProDao eraProDao, PipelinesProperties pipelinesProperties) {

        this.bioSamplesClient = bioSamplesClient;
        this.enaXmlEnhancer = enaXmlEnhancer;
        this.enaElementConverter = enaElementConverter;
        this.eraProDao = eraProDao;
        this.domain = pipelinesProperties.getEnaDomain();
    }

    public EnaCallable build(String accession, boolean suppressionHandler) {
        return new EnaCallable(accession, bioSamplesClient, enaXmlEnhancer,
                enaElementConverter, eraProDao, domain, suppressionHandler);
    }
}
