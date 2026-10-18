-- Removes the demo news of seed_news_20.sql (history and tags first). NOT run automatically: run it by hand on the
-- DEVELOPMENT database, for example in the mysql client:  SOURCE remove_demo_news.sql;
-- The demo articles are the ones whose thumbnail starts with https://picsum.photos/seed/ponleu
START TRANSACTION;
DELETE h FROM news_status_history h JOIN news n ON n.id = h.news_id WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%';
DELETE nt FROM news_tags nt JOIN news n ON n.id = nt.news_id WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%';
DELETE FROM news WHERE thumbnail LIKE 'https://picsum.photos/seed/ponleu%';
COMMIT;
