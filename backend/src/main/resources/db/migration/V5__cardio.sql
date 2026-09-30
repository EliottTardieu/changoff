CREATE TABLE cardio_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    activity VARCHAR(12) NOT NULL CHECK (activity IN ('rope','running','cycling')),
    distance_meters INT,
    reps INT,
    duration_seconds DOUBLE PRECISION NOT NULL CHECK (duration_seconds BETWEEN 0.01 AND 604800),
    standard VARCHAR(6) NOT NULL CHECK (standard IN ('male','female')),
    performed_on DATE NOT NULL,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((activity='rope' AND reps BETWEEN 1 AND 1000000 AND reps IS NOT NULL AND distance_meters IS NULL)
        OR (activity IN ('running','cycling') AND distance_meters BETWEEN 1 AND 2000000 AND distance_meters IS NOT NULL AND reps IS NULL)),
    CHECK (activity <> 'running' OR distance_meters IN (400,1000,5000,10000,20000))
);
CREATE INDEX cardio_sessions_user_date ON cardio_sessions(user_id, performed_on DESC);
