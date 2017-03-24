package uk.ac.ebi.biosamples.solr.repo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.solr.core.SolrOperations;
import org.springframework.data.solr.core.query.FacetOptions;
import org.springframework.data.solr.core.query.FacetQuery;
import org.springframework.data.solr.core.query.FilterQuery;
import org.springframework.data.solr.core.query.Query;
import org.springframework.data.solr.core.query.SimpleFacetQuery;
import org.springframework.data.solr.core.query.SimpleFilterQuery;
import org.springframework.data.solr.core.query.SimpleStringCriteria;
import org.springframework.data.solr.core.query.result.FacetFieldEntry;
import org.springframework.data.solr.core.query.result.FacetPage;
import org.springframework.stereotype.Component;

import uk.ac.ebi.biosamples.solr.model.SolrSample;

@Component
public class SolrSampleRepositoryImpl implements SolrSampleRepositoryCustom {

	@Autowired
	private SolrOperations solrOperations;

	@Override
	//TODO cacheing
	public Page<FacetFieldEntry> getFacetFields(FacetQuery query, Pageable facetPageable) {
		//configure the facet options to use the attribute types fields
		//and to have the appropriate paging
		FacetOptions facetOptions = new FacetOptions();
		facetOptions.addFacetOnField("attributetypes_ss");
		facetOptions.setPageable(facetPageable);

		query.setFacetOptions(facetOptions);
		//execute the query against the solr server
		FacetPage<SolrSample> page = solrOperations.queryForFacetPage(query, SolrSample.class);
		return page.getFacetResultPage("attributetypes_ss");
	}

	@Override
	public FacetPage<?> getFacets(FacetQuery query, List<String> facetFields, Pageable facetPageable) {
		
		if (facetFields == null || facetFields.size() == 0) {
			throw new IllegalArgumentException("Must provide fields to facet on");
		}
		
		//configure the facet options to use the provided fields
		//and to have the appropriate paging
		FacetOptions facetOptions = new FacetOptions();
		for (String field : facetFields) {
			facetOptions.addFacetOnField(field);
		}
		facetOptions.setPageable(facetPageable);
	

		query.setFacetOptions(facetOptions);
		//execute the query against the solr server
		FacetPage<SolrSample> page = solrOperations.queryForFacetPage(query, SolrSample.class);
		return page;
		
	}

	@Override
	public Page<SolrSample> findByQuery(Query query) {
		return solrOperations.query(query, SolrSample.class);
	}
	
	
}