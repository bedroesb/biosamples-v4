package uk.ac.ebi.biosamples.curation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.curation.service.IriUrlValidatorService;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Curation;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.ols.OlsProcessor;
import uk.ac.ebi.biosamples.service.CurationApplicationService;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SampleCurationCallable implements Callable<Integer> {
    private Logger log = LoggerFactory.getLogger(getClass());
    private final Sample sample;
    private final BioSamplesClient bioSamplesClient;
    private final OlsProcessor olsProcessor;
    private final CurationApplicationService curationApplicationService;
    private final IriUrlValidatorService iriUrlValidatorService;
    private final String domain;
    private int curationCount;

    public static final String[] NON_APPLICABLE_SYNONYMS = {"n/a", "na", "n.a", "none",
            "unknown", "--", ".", "null", "missing", "[not reported]",
            "[not requested]", "not applicable", "not_applicable", "not collected", "not specified", "not known", "not reported", "missing: not provided"};

    public static final ConcurrentLinkedQueue<String> failedQueue = new ConcurrentLinkedQueue<>();

    public SampleCurationCallable(BioSamplesClient bioSamplesClient, Sample sample,
                                  OlsProcessor olsProcessor,
                                  CurationApplicationService curationApplicationService,
                                  String domain, IriUrlValidatorService iriUrlValidatorService) {
        this.bioSamplesClient = bioSamplesClient;
        this.sample = sample;
        this.olsProcessor = olsProcessor;
        this.curationApplicationService = curationApplicationService;
        this.domain = domain;
        this.iriUrlValidatorService = iriUrlValidatorService;
        this.curationCount = 0;
    }

    @Override
    public Integer call() {
        try {
            Sample last;
            Sample curated = sample;

            do {
                last = curated;
                curated = curate(last);
            } while (!last.equals(curated));

            do {
                last = curated;
                curated = ols(last);
            } while (!last.equals(curated));

        } catch (Exception e) {
            log.warn("Encountered exception with " + sample.getAccession(), e);
            failedQueue.add(sample.getAccession());
        }

        return curationCount;
    }

    public Sample curate(Sample sample) {
            for (Attribute attribute : sample.getAttributes()) {
                // clean unexpected characters
                String newType = cleanString(attribute.getType());
                String newValue = cleanString(attribute.getValue());

                //if the clean type or value would be empty, curate to an non attribute
                if (newType.length() == 0 || newValue.length() == 0) {
                    Curation curation = Curation.build(Collections.singleton(attribute), Collections.emptyList());

                    bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                    sample = curationApplicationService.applyCurationToSample(sample, curation);
                    curationCount++;

                    return sample;
                }

                if (!attribute.getType().equals(newType) || !attribute.getValue().equals(newValue)) {
                    Attribute newAttribute = Attribute.build(newType, newValue, attribute.getTag(), attribute.getIri(),
                            attribute.getUnit());
                    Curation curation = Curation.build(attribute, newAttribute);

                    bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                    sample = curationApplicationService.applyCurationToSample(sample, curation);
                    curationCount++;

                    return sample;
                }

                // if no information content, remove
                if (isNotApplicableSynonym(attribute.getValue())) {
                    Curation curation = Curation.build(attribute, null);

                    bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                    sample = curationApplicationService.applyCurationToSample(sample, curation);
                    curationCount++;

                    return sample;
                }

                // if it has a unit, make sure it is clean
                if (attribute.getUnit() != null) {
                    String newUnit = correctUnit(attribute.getUnit());

                    if (!attribute.getUnit().equals(newUnit)) {
                        Attribute newAttribute = Attribute.build(attribute.getType(), attribute.getValue(),
                                attribute.getTag(), attribute.getIri(), newUnit);
                        Curation curation = Curation.build(attribute, newAttribute);

                        bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                        sample = curationApplicationService.applyCurationToSample(sample, curation);
                        curationCount++;

                        return sample;
                    }
                }

                //if it is an organism with a single numeric IRI, assume NCBI taxon
                if (attribute.getType().toLowerCase().equals("organism") && attribute.getIri().size() == 1) {
                    Integer taxId = null;

                    try {
                        taxId = Integer.parseInt(attribute.getIri().first());
                    } catch (NumberFormatException ignored) {
                    }

                    if (taxId != null) {
                        SortedSet<String> iris = new TreeSet<>();

                        iris.add("http://purl.obolibrary.org/obo/NCBITaxon_" + taxId);
                        //TODO check this IRI exists via OLS

                        Attribute newAttribute = Attribute.build(attribute.getType(), attribute.getValue(), attribute.getTag(),
                                iris, attribute.getUnit());
                        Curation curation = Curation.build(attribute, newAttribute);

                        bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                        sample = curationApplicationService.applyCurationToSample(sample, curation);
                        curationCount++;

                        return sample;
                    }
                }
            }
            // TODO validate existing ontology terms against OLS

            // TODO turn attributes with biosample accessions into relationships

            // TODO split number+unit attributes

            // TODO lowercase attribute types
            // TODO lowercase relationship types
        return sample;
    }

    public String cleanString(String string) {
        if (string == null) {
            return null;
        }
        // purge all strange characters not-quite-whitespace
        // note, you can find these unicode codes by pasting u"the character"
        // into python
        string = string.replaceAll("\"", "");
        string = string.replaceAll("\n", "");
        string = string.replaceAll("\t", "");
        string = string.replaceAll("\u2011", "-"); // hypen
        string = string.replaceAll("\u2012", "-"); // hypen
        string = string.replaceAll("\u2013", "-"); // hypen
        string = string.replaceAll("\u2014", "-"); // hypen
        string = string.replaceAll("\u2015", "-"); // hypen
        string = string.replaceAll("\u2009", " "); // thin space
        string = string.replaceAll("\u00A0", " "); // non-breaking space
        string = string.replaceAll("\uff09", ") "); // full-width right
        // parenthesis
        string = string.replaceAll("\uff08", " ("); // full-width left
        // parenthesis

        // replace underscores with spaces
        // string = string.replaceAll("_", " ");
        //this is a significant change, so leave it undone for the moment....

        // <br> or <b>
        string = string.replaceAll("\\s*</?[bB][rR]? ?/?>\\s*", " ");
        // <p>
        string = string.replaceAll("\\s*</?[pP] ?/?>\\s*", " ");
        // <i>
        string = string.replaceAll("\\s*</?[iI] ?/?>\\s*", " ");

        // trim extra whitespace at start and end
        string = string.trim();

        // XML/HTML automatically replaces consecutive spaces with single spaces
        // TODO use regex for any series of whitespace characters or equivalent
        while (string.contains("  ")) {
            string = string.replace("  ", " ");
        }

        // some UTF-8 hacks
        // TODO replace with code from Solr UTF-8 plugin
        string = string.replaceAll("ÃƒÂ¼", "ü");

        // also strip UTF-8 control characters that invalidate XML
        // from
        // http://blog.mark-mclaren.info/2007/02/invalid-xml-characters-when-valid-utf8_5873.html
        // TODO check for valid UTF-8 but invalid JSON characters
        StringBuilder sb = new StringBuilder(); // Used to hold the output.
        char current; // Used to reference the current character.
        for (int i = 0; i < string.length(); i++) {
            current = string.charAt(i); // NOTE: No IndexOutOfBoundsException
            // caught here; it should not happen.
            if (current == 0x9 || current == 0xA || current == 0xD || current >= 0x20 && current <= 0xD7FF || current >= 0xE000 && current <= 0xFFFD) {
                sb.append(current);
            }
        }

        return sb.toString();
    }

    private static boolean stringContainsItemFromList(String value) {
        return Arrays.stream(SampleCurationCallable.NON_APPLICABLE_SYNONYMS).parallel().anyMatch(value::equals);
    }

    public static boolean isNotApplicableSynonym(String string) {
        String lsString = string.toLowerCase().trim();

        return stringContainsItemFromList(lsString);
    }

    private String correctUnit(String unit) {
        String lcval = unit.toLowerCase();

        switch (lcval) {
            case "alphanumeric":
            case "na":
            case "n/a":
            case "n.a":
            case "censored/uncensored":
            case "m/f":
            case "test/control":
            case "yes/no":
            case "y/n":
            case "not specified":
            case "not collected":
            case "not known":
            case "not reported":
            case "missing":
                // NOTE -this is for units ONLY

                return null;
            case "meter":
            case "meters":
                return "meter";
            case "cellsperliter":
            case "cells per liter":
            case "cellperliter":
            case "cell per liter":
            case "cellsperlitre":
            case "cells per litre":
            case "cellperlitre":
            case "cell per litre":
                return "cell per liter";
            case "cellspermilliliter":
            case "cells per milliliter":
            case "cellpermilliliter":
            case "cell per milliliter":
            case "cellspermillilitre":
            case "cells per millilitre":
            case "cellpermillilitre":
            case "cell per millilitre":
                return "cell per millilitre";
            case "micromolesperliter":
            case "micromoleperliter":
            case "micromole per liter":
            case "micromoles per liter":
            case "micromolesperlitre":
            case "micromoleperlitre":
            case "micromole per litre":
            case "micromoles per litre":
                return "micromole per liter";
            case "microgramsperliter":
            case "microgramperliter":
            case "microgram per liter":
            case "micrograms per liter":
            case "microgramsperlitre":
            case "microgramperlitre":
            case "microgram per litre":
            case "micrograms per litre":
                return "microgram per liter";
            case "micromolesperkilogram":
            case "micromoles per kilogram":
            case "micromoleperkilogram":
            case "micromole per kilogram":
                return "micromole per kilogram";
            case "psu":
            case "practicalsalinityunit":
            case "practical salinity unit":
            case "practical salinity units":
            case "pss-78":
            case "practicalsalinityscale1978 ":
                // technically, this is not a unit since its dimensionless..
                return "practical salinity unit";
            case "micromoles":
            case "micromole":
                return "micromole";
            case "decimalhours":
            case "decimalhour":
            case "hours":
            case "hour":
                return "hour";
            case "day":
            case "days":
                return "day";
            case "week":
            case "weeks":
                return "week";
            case "month":
            case "months":
                return "month";
            case "year":
            case "years":
                return "year";
            case "percentage":
                return "percent";
            case "decimal degrees":
            case "decimal degree":
            case "decimaldegrees":
            case "decimaldegree":
                return "decimal degree";
            case "celcius":
            case "degree celcius":
            case "degrees celcius":
            case "degreecelcius":
            case "degree celsius":
            case "degrees celsius":
            case "degreecelsius":
            case "centigrade":
            case "degree centigrade":
            case "degrees centigrade":
            case "degreecentigrade":
            case "c":
            case "??c":
            case "degree c":
            case "internationaltemperaturescale1990":
            case "iternationaltemperaturescale1990":
                return "Celsius";
            default:
                // no change
                return unit;
        }
    }

    private Sample ols(Sample sample) {
        for (final Attribute attribute : sample.getAttributes()) {
            final Set<String> iriSet = new TreeSet<>(attribute.getIri());

            for (String iri : attribute.getIri()) {
                log.trace("Checking iri " + iri);

                if (iri.matches("^[A-Za-z]+[_:\\-][0-9]+$")) {
                    log.trace("Querying OLS for iri " + iri);
                    final Optional<String> iriResult = olsProcessor.queryOlsForShortcode(iri);

                    if (iriResult.isPresent()) {
                        log.trace("Mapped " + iri + " to " + iriResult.get());
                        final Attribute mapped = Attribute.build(attribute.getType(), attribute.getValue(), attribute.getTag(), iriResult.get(), null);
                        final Curation curation = Curation.build(Collections.singleton(attribute), Collections.singleton(mapped), null, null);

                        //save the curation back in biosamples
                        bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                        sample = curationApplicationService.applyCurationToSample(sample, curation);

                        return sample;
                    }
                }
                // Validate the IRI URL and do a HTTP call for URL's matching the less common pattern, remove URL's as Curation objects for any thats doesn't return back a 2xx response
                 else if (iriUrlValidatorService.checkUrlForPattern(iri)) {
                    if (!iriUrlValidatorService.validateIri(iri)) {
                        iriSet.remove(iri);
                        final Attribute mapped = Attribute.build(attribute.getType(), attribute.getValue(), attribute.getTag(), iriSet, null);
                        final Curation curation = Curation.build(Collections.singleton(attribute), Collections.singleton(mapped), null, null);

                        //save the curation back in biosamples
                        bioSamplesClient.persistCuration(sample.getAccession(), curation, domain);
                        sample = curationApplicationService.applyCurationToSample(sample, curation);

                        return sample;
                    }
                }
            }
        }

        return sample;
    }
}
