package uk.ac.ebi.biosamples.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.*;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biosamples.model.facets.Facet;
import uk.ac.ebi.biosamples.model.facets.StringListFacet;
import uk.ac.ebi.biosamples.service.FacetService;
import uk.ac.ebi.biosamples.service.FilterService;

import java.util.List;

@RestController
@ExposesResourceFor(Facet.class)
@RequestMapping("/samples/facets")
public class SampleFacetRestController {

	private final FacetService facetService;
	private final FilterService filterService;


	private final EntityLinks entityLinks;
	
	private Logger log = LoggerFactory.getLogger(getClass());

	public SampleFacetRestController(FacetService facetService, FilterService filterService,
									 EntityLinks entityLinks) {
		this.facetService = facetService;
		this.filterService = filterService;
		this.entityLinks = entityLinks;
	}
    


    @CrossOrigin
	@GetMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
	public ResponseEntity<Resources<StringListFacet>> getFacetsHal(
			@RequestParam(name="text", required=false) String text,
			@RequestParam(name="filter", required=false) String[] filter) {
    	
    	//TODO support rows and start parameters
		MultiValueMap<String, String> filters = filterService.getFilters(filter);
    	List<StringListFacet> sampleFacets = facetService.getFacets(text, filterService.getFilters(filter), 10, 10);

//    	PagedResources<StringListFacet> resources = new PagedResources<>(
//    			sampleFacets,
//				new PagedResources.PageMetadata(10, 1, 10, 5));
        Resources<StringListFacet> resources = new Resources<>(sampleFacets);
    	
		//Links for the entire page
		//this is hacky, but no clear way to do this in spring-hateoas currently
    	resources.removeLinks();
    			
    	//to generate the HAL template correctly, the parameter name must match the requestparam name
		resources.add(ControllerLinkBuilder.linkTo(
				ControllerLinkBuilder.methodOn(SampleFacetRestController.class)
					.getFacetsHal(text, filter))
				.withSelfRel());

		resources.add(ControllerLinkBuilder.linkTo(
				ControllerLinkBuilder.methodOn(SamplesRestController.class)
					.searchHal(text, null, null, filter,null, null))
                    .withRel("samples"));
		
		
		return ResponseEntity.ok().body(resources);
	}
}
