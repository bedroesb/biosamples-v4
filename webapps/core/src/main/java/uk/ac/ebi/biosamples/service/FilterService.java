package uk.ac.ebi.biosamples.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.biosamples.model.facets.FacetType;
import uk.ac.ebi.biosamples.model.filters.*;

import java.io.UnsupportedEncodingException;
import java.util.*;


@Service
public class FilterService {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	public MultiValueMap<String,String> getFilters(String[] filterStrings) {
		if (filterStrings == null) return new LinkedMultiValueMap<>();
		if (filterStrings.length == 0) return new LinkedMultiValueMap<>();
		//sort the array
		Arrays.sort(filterStrings);
		SortedSet<String> filterStringSet = new TreeSet<>(Arrays.asList(filterStrings));
		//strip the requestParams down to just the selected facet information
		MultiValueMap<String, String> filters = new LinkedMultiValueMap<>();
		for (String filterString : filterStringSet) {
			log.info("looking at filter string '" + filterString + "'");
			if (filterString.contains(":")) {
				// Assume filter format is FacetType:FacetLabel:FacetLabelValue

				String[] filterParts = filterString.split(":", 3);
				String key = filterParts[0] + ":" + filterParts[1];
				String value = null;
				if (filterParts.length > 2) {
					value = filterParts[2];
				}
				filters.add(decodeParam(key), decodeParam(value));
				log.info("adding filter " + key + " = " + value);
			}
		}
		return filters;
	}

	public Collection<Filter> getFiltersCollection(String[] filterStrings) {
		Collection<Filter> outputFilters = new ArrayList<>();
		if (filterStrings == null) return outputFilters;
		if (filterStrings.length == 0) return outputFilters;


		/**
		 *	For every filter I need to extract:
		 *	1. The kind of the filter
		 *  2. Label (which will be used to get the corresponding field in solr, so here is decoded)
		 *  3. The value
 		 */
		//TODO re-enable this
//		Arrays.sort(filterStrings);
//		SortedSet<String> filterStringSet = new TreeSet<>(Arrays.asList(filterStrings));
//		for(String filterString: filterStringSet) {
//			FilterType filterType = FilterType.ofFilterString(filterString);
//			String filterValue = filterString.replace(filterType.getKind() + ":","");
//			Filter filter = getFilter(filterValue, filterType);
//			outputFilters.add(filter);
//		}

		return outputFilters;

	}

	private Filter getFilter(String serializedValue, FilterType filterType) {
		String filterLabel = "";
		FilterContent filterContent = new EmptyFilter();
		switch(filterType) {
			case ATTRIBUTE_FILTER:
				String[] valueElements = serializedValue.split(":", 2);
				filterLabel = valueElements[0];
				if(valueElements.length > 1) {
					filterContent = new ValueFilter(valueElements[1]);
				}

		}

		return new Filter(filterType, filterLabel, filterContent);
	}

	private String encodeParam(String queryParam) {
		try {
			return UriUtils.encodeQueryParam(queryParam, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private String decodeParam(String queryParam) {
		try {
			return UriUtils.decode(queryParam, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 *	TODO: Duplication of code - we should think of something different
	 *	This could lead to problems where for a FilterType no facet is available
	 *  The code is duplicated over {@see package uk.ac.ebi.biosamples.service.FacetService#from} class
 	 */

	public static FacetType from(FilterType type) {
	    switch(type) {
			case ATTRIBUTE_FILTER:
				return FacetType.ATTRIBUTE;
			case RELATION_FILER:
				return FacetType.OUTGOING_RELATIONSHIP;
			case INVERSE_RELATION_FILTER:
				return FacetType.INCOMING_RELATIONSHIP;
			case DATE_FILTER:
				return FacetType.DATE;
			default:
			    throw new RuntimeException("No facet type is associated to the filter type " + type);
		}
	}


}
