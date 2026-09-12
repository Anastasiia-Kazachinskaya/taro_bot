CREATE OR REPLACE FUNCTION consume_quota(
    p_user_id BIGINT,
    p_day DATE,
    p_readings_limit INT,
    p_llm_limit INT,
    p_is_reading BOOLEAN
) RETURNS TABLE(allowed BOOLEAN, reason TEXT, readings_used INT, llm_used INT) AS $$
DECLARE
    r usage%ROWTYPE;
BEGIN
    INSERT INTO usage (user_id, day) VALUES (p_user_id, p_day) ON CONFLICT DO NOTHING;

    SELECT * INTO r FROM usage WHERE user_id = p_user_id AND day = p_day FOR UPDATE;

    IF p_is_reading AND p_readings_limit > 0 AND r.readings >= p_readings_limit THEN
        RETURN QUERY SELECT false, 'readings'::TEXT, r.readings, r.llm_requests;
        RETURN;
    END IF;

    IF p_llm_limit > 0 AND r.llm_requests >= p_llm_limit THEN
        RETURN QUERY SELECT false, 'llm'::TEXT, r.readings, r.llm_requests;
        RETURN;
    END IF;

    UPDATE usage
    SET readings = readings + (CASE WHEN p_is_reading THEN 1 ELSE 0 END),
        llm_requests = llm_requests + 1
    WHERE user_id = p_user_id AND day = p_day
    RETURNING readings, llm_requests INTO r.readings, r.llm_requests;

    RETURN QUERY SELECT true, ''::TEXT, r.readings, r.llm_requests;
END;
$$ LANGUAGE plpgsql;
