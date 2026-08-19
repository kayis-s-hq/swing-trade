-- Expand model_version to accommodate full model paths and longer model names
ALTER TABLE sentiment_results ALTER COLUMN model_version TYPE VARCHAR(255);