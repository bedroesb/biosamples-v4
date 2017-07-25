#!/bin/bash
set -e

./docker-webapp.sh --clean

source docker-env.sh

#start up the agents
#docker-compose up -d biosamples-agents-solr biosamples-agents-neo4j biosamples-agents-curation
docker-compose up -d biosamples-agents-solr biosamples-agents-curation
#docker-compose scale biosamples-agents-solr=1 biosamples-agents-neo4j=5

#import from NCBI
echo "Importing from NCBI"
ARGS=
ARGS="$ARGS --biosamples.client.uri=http://localhost:8081/biosamples/beta" 
ARGS="$ARGS --biosamples.ncbi.file=/home/faulcon/Desktop/biosample_set.xml.gz"
ARGS="$ARGS --logging.file=./docker/logs/pipelines-ncbi.log"
#wget -O /home/faulcon/Desktop/biosample_set.xml.gz http://ftp.ncbi.nih.gov/biosample/biosample_set.xml.gz 
time java -jar pipelines/ncbi/target/pipelines-ncbi-4.0.0-SNAPSHOT.jar $ARGS
echo "Imported from NCBI"

#pre-assign existing biosample accessions FIRST
ARGS=
ARGS="$ARGS --biosamples.client.uri=http://localhost:8081/biosamples/beta" 
ARGS="$ARGS --logging.file=./docker/logs/pipelines-accession.log"
time java -jar pipelines/accession/target/pipelines-accession-4.0.0-SNAPSHOT.jar $ARGS

#import sampletab submissions
echo "Importing SampleTab submissions"
export SUBS_HOME=/home/faulcon/Desktop/submissions
#rsync -zarv --prune-empty-dirs --include="*/" --include="sampletab.toload.txt" --exclude="*" ebi-cli.ebi.ac.uk:/ebi/microarray/home/biosamples/production/data/GSB/ $SUBS_HOME
#ls $SUBS_HOME/GSB-*/sampletab.toload.txt | xargs -n 1 -P 4 -I {} curl -X POST -H "Content-Type: text/plain" --data-binary @{} http://localhost:8082/biosamples/beta/sampletab/v4
echo "Imported SampleTab submissions"

#import from arrayexpress
echo "Importing ArrayExpress submissions"
export SUBS_HOME=/home/faulcon/Desktop/submissions
#rsync -zarv --prune-empty-dirs --include="*/" --include="sampletab.toload.txt" --exclude="*" ebi-cli.ebi.ac.uk:/ebi/microarray/home/biosamples/production/data/ae/ $SUBS_HOME
#ls $SUBS_HOME/GAE-*/sampletab.toload.txt | xargs -n 1 -P 4 -I {} curl -X POST -H "Content-Type: text/plain" --data-binary @{} http://localhost:8082/biosamples/beta/sampletab/v4
echo "Imported ArrayExpress submissions"

#import from ena
echo "Importing from ENA"
ARGS=
ARGS="$ARGS --biosamples.client.uri=http://localhost:8081/biosamples/beta" 
ARGS="$ARGS --logging.file=./docker/logs/pipelines-ena.log"
time java -jar pipelines/ena/target/pipelines-ena-4.0.0-SNAPSHOT.jar $ARGS
echo "Imported from ENA"

