package uk.ac.ebi.biosamples.service;

import java.time.format.DateTimeFormatter;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;

import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.ExternalReference;
import uk.ac.ebi.biosamples.model.Sample;

@Service
@ConfigurationPropertiesBinding
public class SampleToXmlConverter implements Converter<Sample, Document> {
	
	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
	
	private final Namespace xmlns = Namespace.get("http://www.ebi.ac.uk/biosamples/SampleGroupExport/1.0");
	private final Namespace xsi = Namespace.get("xsi", "http://www.w3.org/2001/XMLSchema-instance");
	private final ExternalReferenceNicknameService externalReferenceNicknameService;
	
	
	public SampleToXmlConverter(ExternalReferenceNicknameService externalReferenceNicknameService) {
		this.externalReferenceNicknameService = externalReferenceNicknameService;
	}
	
	@Override
	public Document convert(Sample source) {
        Document doc = DocumentHelper.createDocument();
        
		Element bioSample = doc.addElement("BioSample");
		
		bioSample.add(xmlns);
		bioSample.addAttribute("xsi:schemaLocation", "http://www.ebi.ac.uk/biosamples/SampleGroupExport/1.0 http://www.ebi.ac.uk/biosamples/assets/xsd/v1.0/BioSDSchema.xsd");
		bioSample.add(xsi);
		
		bioSample.addAttribute("id", source.getAccession());
		bioSample.addAttribute("submissionUpdateDate", dateTimeFormatter.format(source.getUpdate())+"+00:00");
		bioSample.addAttribute("submissionReleaseDate", dateTimeFormatter.format(source.getRelease())+"+00:00");

		Element e = bioSample.addElement(QName.get("Property", xmlns));
		//Element e = parent.addElement("Property");
		e.addAttribute("class", "Sample Name");
		e.addAttribute("characteristic", "false");
		e.addAttribute("comment", "false");
		e.addAttribute("type", "STRING");		
		Element qv = e.addElement("QualifiedValue");
		Element v = qv.addElement("Value");
		v.addText(source.getName());
		
		SortedMap<String, SortedSet<String>> attrTypeValue = new TreeMap<>();
		SortedMap<String, SortedMap<String, String>> attrIri = new TreeMap<>();
		SortedMap<String, SortedMap<String, String>> attrUnit = new TreeMap<>();
		
		for (Attribute attribute : source.getCharacteristics()) {

/*
	<Property class="Sample Name" characteristic="false" comment="false"
		type="STRING">
		<QualifiedValue>
			<Value>Test Sample</Value>
			<TermSourceREF>
				<Name />
				<TermSourceID>http://purl.obolibrary.org/obo/NCBITaxon_9606</TermSourceID>
			</TermSourceREF>
			<Unit>year</Unit>
		</QualifiedValue>
	</Property>		
 */
			if (!attrTypeValue.containsKey(attribute.getType())) {
				attrTypeValue.put(attribute.getType(), new TreeSet<>());
				attrIri.put(attribute.getType(), new TreeMap<>());
				attrUnit.put(attribute.getType(), new TreeMap<>());
			}
			attrTypeValue.get(attribute.getType()).add(attribute.getValue());
			
			if (attribute.getIri() != null && attribute.getIri().toString().length() > 0) {
				attrIri.get(attribute.getType()).put(attribute.getValue(), attribute.getIri().toString());
			}

			if (attribute.getUnit() != null && attribute.getUnit().trim().length() > 0) {
				attrUnit.get(attribute.getType()).put(attribute.getValue(), attribute.getUnit());
			}
		}
		
		for (String attributeType : attrTypeValue.keySet()) {
			Element property = bioSample.addElement(QName.get("Property", xmlns));
			//Element e = parent.addElement("Property");
			property.addAttribute("class", attributeType);
			property.addAttribute("characteristic", "false");
			property.addAttribute("comment", "false");
			property.addAttribute("type", "STRING");		
			
			for (String attributeValue : attrTypeValue.get(attributeType)) {
				Element qualifiedValue = property.addElement("QualifiedValue");
				Element value = qualifiedValue.addElement("Value");
				value.addText(attributeValue);
				
				if (attrIri.get(attributeType).containsKey(attributeValue)) {
					Element termSourceRef = qualifiedValue.addElement("TermSourceREF");
					termSourceRef.addElement("Name");
					Element termSourceId = termSourceRef.addElement("TermSourceID");
					termSourceId.setText(attrIri.get(attributeType).get(attributeValue));					
				}
				
				if (attrUnit.get(attributeType).containsKey(attributeValue)) {
					Element unitE = qualifiedValue.addElement("Unit");
					unitE.setText(attrUnit.get(attributeType).get(attributeValue));					
				}
			}
		}
		
		//TODO derivedFrom element
		
		for (ExternalReference externalReference : source.getExternalReferences()) {
			/*
			  <Database>
			    <ID>ERS1463623</ID>
			    <Name>ENA</Name>
			    <URI>http://www.ebi.ac.uk/ena/data/view/ERS1463623</URI>
			  </Database>
		 	*/	
			Element database = bioSample.addElement(QName.get("Database", xmlns));
			//Element databaseId = database.addElement(QName.get("ID", xmlns));
			//TODO work out how to get the databaseid
			Element databaseName = database.addElement(QName.get("Name", xmlns));
			databaseName.setText(externalReferenceNicknameService.getNickname(externalReference));
			Element databaseUri = database.addElement(QName.get("URI", xmlns));
			databaseUri.setText(externalReference.getUrl());
		}
		return doc;
	}


}
