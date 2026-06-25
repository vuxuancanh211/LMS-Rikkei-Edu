UPDATE forum_posts
SET topic = CASE upper(trim(topic))
    WHEN 'ANNOUNCEMENT' THEN 'announcement'
    WHEN 'QUESTION' THEN 'qa'
    WHEN 'QA' THEN 'qa'
    WHEN 'SHARE' THEN 'share'
    WHEN 'IDEA' THEN 'idea'
    WHEN 'DISCUSSION' THEN 'discussion'
    ELSE lower(trim(topic))
END
WHERE topic IS NOT NULL
  AND trim(topic) <> '';

UPDATE forum_posts
SET topic = 'discussion'
WHERE topic IS NULL
   OR trim(topic) = '';

ALTER TABLE forum_posts
    ALTER COLUMN topic SET NOT NULL;
