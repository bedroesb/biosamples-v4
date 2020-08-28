/*
* Copyright 2019 EMBL - European Bioinformatics Institute
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
* file except in compliance with the License. You may obtain a copy of the License at
* http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied. See the License for the
* specific language governing permissions and limitations under the License.
*/
package uk.ac.ebi.biosamples.solr.service;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.solr.core.query.*;
import org.springframework.data.solr.core.query.result.FacetFieldEntry;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biosamples.BioSamplesProperties;
import uk.ac.ebi.biosamples.model.facet.Facet;
import uk.ac.ebi.biosamples.model.filter.Filter;
import uk.ac.ebi.biosamples.solr.model.field.SolrSampleField;
import uk.ac.ebi.biosamples.solr.repo.SolrSampleRepository;

@Service
public class SolrFacetService {

  private static final int TIMEALLOWED = 30;
  private final SolrSampleRepository solrSampleRepository;
  private final SolrFieldService solrFieldService;
  private Logger log = LoggerFactory.getLogger(getClass());
  private final SolrFilterService solrFilterService;

  private static final List<String> FACETING_FIELDS = List.of("organism", "sex", "tissue", "strain", "organism part",
          "cell type", "isolate", "sample type", "genotype", "isolation source", "histological type", "age", "host");
  private static final List<String> FACETING_FIELDS_REL = List.of("release_dt");
  private static final List<String> FACETING_FIELDS_RANGE = List.of("release_dt");

  public SolrFacetService(
      SolrSampleRepository solrSampleRepository,
      SolrFieldService solrFieldService,
      SolrFilterService solrFilterService,
      BioSamplesProperties bioSamplesProperties) {
    this.solrSampleRepository = solrSampleRepository;
    this.solrFieldService = solrFieldService;
    this.solrFilterService = solrFilterService;
  }

  public List<Facet> getFacets(
      String searchTerm,
      Collection<Filter> filters,
      Collection<String> domains,
      Pageable facetFieldPageInfo,
      Pageable facetValuesPageInfo) {
    // default to search all
    if (searchTerm == null || searchTerm.trim().length() == 0) {
      searchTerm = "*:*";
    }

    List<Facet> facets = new ArrayList<>();

    // build a query out of the users string and any facets
    FacetQuery query = new SimpleFacetQuery();
    query.addCriteria(new Criteria().expression(searchTerm));
    query.setTimeAllowed(TIMEALLOWED * 1000);

    // Add domains and release date filters
    Optional<FilterQuery> domainAndPublicFilterQuery =
        solrFilterService.getPublicFilterQuery(domains);
    domainAndPublicFilterQuery.ifPresent(query::addFilterQuery);

    // Add all the provided filters
    Optional<FilterQuery> optionalFilter = solrFilterService.getFilterQuery(filters);
    optionalFilter.ifPresent(query::addFilterQuery);

    // Generate a facet query to get all the available facets for the samples
    Page<FacetFieldEntry> facetFields =
        solrSampleRepository.getFacetFields(query, facetFieldPageInfo);

    // Get the facet fields
    // TODO implement hashing function
    List<Entry<SolrSampleField, Long>> allFacetFields = new ArrayList<>();
    for (FacetFieldEntry ffe : facetFields) {

      Long facetFieldCount = ffe.getValueCount();
      SolrSampleField solrSampleField = this.solrFieldService.decodeField(ffe.getValue());
      allFacetFields.add(new SimpleEntry<>(solrSampleField, facetFieldCount));
    }

    /*
       Then based on the facet type I need to create a specific facet query
       1. _ir_ss => regular facet
       2. _av_ss => regular facet
       3. _dt => range facet
    */

    // TODO do this to properly account for different strategies - this is a dirty hack for
    // performance!
    /*
          for (Entry<SolrSampleField, Long> fieldCountEntry: allFacetFields) {
              FacetFetchStrategy strategy = fieldCountEntry.getKey().getFacetCollectionStrategy();
              List<Optional<Facet>> optionalFacets = strategy.fetchFacetsUsing(solrSampleRepository,
                      query,
                      Collections.singletonList(fieldCountEntry),
                      facetValuesPageInfo);
              optionalFacets.forEach(opt -> opt.ifPresent(facets::add));
          }
    */

    if (allFacetFields.size() > 0) {
      allFacetFields
          .get(0)
          .getKey()
          .getFacetCollectionStrategy()
          .fetchFacetsUsing(solrSampleRepository, query, allFacetFields, facetValuesPageInfo)
          .forEach(opt -> opt.ifPresent(facets::add));
    }

    // Return the list of facets
    Collections.sort(facets);
    Collections.reverse(facets);

    return facets;
  }

  public List<Facet> getFacets2(
          String searchTerm,
          Collection<Filter> filters,
          Collection<String> domains,
          Pageable facetFieldPageInfo,
          Pageable facetValuesPageInfo) {
    // default to search all
    if (searchTerm == null || searchTerm.trim().length() == 0) {
      searchTerm = "*:*";
    }

    List<Facet> facets = new ArrayList<>();

    // build a query out of the users string and any facets
    FacetQuery query = new SimpleFacetQuery();
    query.addCriteria(new Criteria().expression(searchTerm));
    query.setTimeAllowed(TIMEALLOWED * 1000);

    // Add domains and release date filters
    Optional<FilterQuery> domainAndPublicFilterQuery =
            solrFilterService.getPublicFilterQuery(domains);
    domainAndPublicFilterQuery.ifPresent(query::addFilterQuery);

    // Add all the provided filters
    Optional<FilterQuery> optionalFilter = solrFilterService.getFilterQuery(filters);
    optionalFilter.ifPresent(query::addFilterQuery);

    // Generate a facet query to get all the available facets for the samples
    Page<FacetFieldEntry> facetFields =
            solrSampleRepository.getFacetFields(query, facetFieldPageInfo);

    // Get the facet fields
    // TODO implement hashing function
    List<Entry<SolrSampleField, Long>> allFacetFields = new ArrayList<>();
    for (FacetFieldEntry ffe : facetFields) {

      Long facetFieldCount = ffe.getValueCount();
      SolrSampleField solrSampleField = this.solrFieldService.decodeField(ffe.getValue());
      allFacetFields.add(new SimpleEntry<>(solrSampleField, facetFieldCount));
    }

    allFacetFields = new ArrayList<>();
    allFacetFields.add((new SimpleEntry<>(this.solrFieldService.decodeField(SolrFieldService.encodeFieldName("Organism") + "_av_ss"), 100L)));
    allFacetFields.add((new SimpleEntry<>(this.solrFieldService.decodeField(SolrFieldService.encodeFieldName("organism") + "_av_ss"), 2L)));
    allFacetFields.add((new SimpleEntry<>(this.solrFieldService.decodeField(SolrFieldService.encodeFieldName("sex") + "_av_ss"), 52L)));

    if (allFacetFields.size() > 0) {
      allFacetFields
              .get(0)
              .getKey()
              .getFacetCollectionStrategy()
              .fetchFacetsUsing(solrSampleRepository, query, allFacetFields, facetValuesPageInfo)
              .forEach(opt -> opt.ifPresent(facets::add));
    }

    // Return the list of facets
    Collections.sort(facets);
    Collections.reverse(facets);

    return facets;
  }

}
