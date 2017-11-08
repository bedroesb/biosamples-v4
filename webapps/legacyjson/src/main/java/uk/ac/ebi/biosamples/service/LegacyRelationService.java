package uk.ac.ebi.biosamples.service;

import org.springframework.stereotype.Service;
import uk.ac.ebi.biosamples.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class LegacyRelationService {

    private final KnownLegacyRelationshipMapping relationshipMapping;
    private final SampleService sampleService;

    public LegacyRelationService(SampleService sampleService) {
        this.relationshipMapping = new KnownLegacyRelationshipMapping();
        this.sampleService = sampleService;
    }

    public List<LegacyGroupsRelations> getGroupsRelationships(String accession) {
                return groupsRelatedTo(sampleService.findByAccession(accession)).stream()
                        .map(LegacyGroupsRelations::new)
                        .collect(Collectors.toList());
    }

    public List<LegacySamplesRelations> getSamplesRelations(String accession, String relationshipType) {
        return samplesRelatedTo(sampleService.findByAccession(accession), relationshipType).stream()
                .map(LegacySamplesRelations::new)
                .collect(Collectors.toList());

    }

    public List<Sample> samplesRelatedTo(Sample sample, String relationType) {

        List<Relationship> validRelationships = new ArrayList<>();

        for (Relationship rel: sample.getRelationships()) {
            if (relationshipsOfTypeAndAccession(relationType).test(rel)) {
                validRelationships.add(rel);
            }
        }

        List<Sample> relatedSamples = new ArrayList<>();
        for (Relationship rel: validRelationships) {

            String relatedSampleAccession = rel.getSource().equals(sample.getAccession()) ? rel.getTarget() : rel.getSource();
            relatedSamples.add(sampleService.findByAccession(relatedSampleAccession));
        }
        return relatedSamples;
    }

    public List<Sample> groupsRelatedTo(Sample sample) {

        return sample.getRelationships().stream()
                .filter(groupRelationships())
                .map(r -> r.getType().equals("groups") ? r.getTarget() : r.getSource())
                .map(sampleService::findByAccession)
                .collect(Collectors.toList());

    }

    private Predicate<? super Relationship> groupRelationships() {
        return r -> r.getType().equals("group") || r.getType().equals("has member");
    }


    /**
     * Predicate to filter for relationships of a specific type, accounting also for inverse relationships
     * @param relationshipType
     * @return
     */
    private Predicate<Relationship> relationshipsOfTypeAndAccession(String relationshipType) {

        return r ->
                r.getType().equals(relationshipType) || this.relationshipUsesMappedType(r, relationshipType);

    }

    private boolean relationshipUsesMappedType(Relationship r, String otherType) {
        if (this.relationshipMapping.getMappendRelationship(otherType).isPresent())
            return r.getType().equals(this.relationshipMapping.getMappendRelationship(otherType).get());
        return false;
    }


}
