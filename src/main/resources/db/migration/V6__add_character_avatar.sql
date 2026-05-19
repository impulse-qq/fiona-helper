-- Phase 1: Web UI Character Cards
-- Add avatar_path to story_character for uploaded character avatars

ALTER TABLE story_character ADD COLUMN avatar_path VARCHAR(512);
