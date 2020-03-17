package uk.ac.ebi.biosamples.livelist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiveListPipelineDao {
    @Autowired
    @Qualifier("eraJdbcTemplate")
    protected JdbcTemplate jdbcTemplate;

    private Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Returns SUPPRESSED ENA samples
     *
     * @return
     */
    public List<String> doGetSuppressedEnaSamples() {
        String query = "SELECT UNIQUE(BIOSAMPLE_ID) FROM SAMPLE WHERE BIOSAMPLE_ID LIKE 'SAME%' AND SAMPLE_ID LIKE 'ERS%' AND BIOSAMPLE_AUTHORITY= 'N' AND STATUS_ID = 5";

        return jdbcTemplate.queryForList(query, String.class);
    }
}
