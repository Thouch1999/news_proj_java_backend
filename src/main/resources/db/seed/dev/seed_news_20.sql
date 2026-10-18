-- =====================================================================
-- DEVELOPMENT seed: 20 demo news articles (made-up test data, not real news).
-- Run by DemoNewsSeeder only, when the "dev" profile is active and app.seed.demo-news=true.
-- It is NOT a migration and must never run in production.
--
-- The seeder runs the whole script in one transaction on one connection and first sets
--   @author, @admin, @editor  (the ids of the seeded Author, Admin and Editor users)
-- The categories are looked up by the slugs of DataSeeder; a tag that does not exist is skipped.
-- To remove the data again, run remove_demo_news.sql by hand.
-- =====================================================================

-- ---------------------------------------------------------------------
-- SETTINGS: categories (looked up by slug, no fixed IDs); the users come from the seeder
-- ---------------------------------------------------------------------

SET @cat_business      := (SELECT id FROM categories WHERE slug = 'business'      LIMIT 1);
SET @cat_education     := (SELECT id FROM categories WHERE slug = 'education'     LIMIT 1);
SET @cat_entertainment := (SELECT id FROM categories WHERE slug = 'entertainment' LIMIT 1);
SET @cat_health        := (SELECT id FROM categories WHERE slug = 'health'        LIMIT 1);
SET @cat_society       := (SELECT id FROM categories WHERE slug = 'society'       LIMIT 1);
SET @cat_sports        := (SELECT id FROM categories WHERE slug = 'sports'        LIMIT 1);
SET @cat_technology    := (SELECT id FROM categories WHERE slug = 'technology'    LIMIT 1);

-- ---------------------------------------------------------------------
-- NEWS: 15 published, 1 archived, 2 pending, 1 draft, 1 rejected
-- ---------------------------------------------------------------------
INSERT INTO news
  (title, slug, summary, content, thumbnail, status, author_id, category_id,
   published_at, view_count, comments_enabled, rejection_reason, created_at, updated_at)
VALUES
-- 1
('Cambodian Startups Turn to AI Chatbots for Khmer Customer Service',
 'cambodian-startups-ai-chatbots-khmer-customer-service',
 'Several local startups now use chatbots that understand Khmer to answer customer questions day and night.',
 '<p>A growing number of Cambodian startups are adding chatbots that can read and reply in <strong>Khmer</strong>. Companies say the tools answer simple questions instantly and let staff focus on harder cases.</p><h2>Why it matters</h2><p>Most customers prefer to write in their own language. Until recently, chatbots only worked well in English.</p><ul><li>Answers available 24 hours a day</li><li>Shorter waiting times for customers</li><li>Staff handle complex requests</li></ul>',
 'https://picsum.photos/seed/ponleu01/800/450', 'PUBLISHED', @author, @cat_technology,
 DATE_SUB(NOW(), INTERVAL 1 DAY), 152, 1, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 2
('Public Libraries Add Free 3D Printing Workshops for Teens',
 'public-libraries-free-3d-printing-workshops-teens',
 'Weekend workshops teach teenagers how to design and print simple objects with 3D printers.',
 '<p>Three public libraries have started free <strong>3D printing workshops</strong> for students aged 13 to 18. Each session lasts two hours and covers design basics and printing.</p><h2>How to join</h2><p>Students can register at the library front desk. Places are limited to 15 per session.</p>',
 'https://picsum.photos/seed/ponleu02/800/450', 'PUBLISHED', @author, @cat_technology,
 DATE_SUB(NOW(), INTERVAL 4 DAY), 87, 1, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),

-- 3
('Rice Exporters Report Strong Demand from European Buyers',
 'rice-exporters-strong-demand-european-buyers',
 'Exporters say orders for fragrant rice rose this season, with more buyers asking for organic certification.',
 '<p>Rice exporters report a busy season, with European buyers ordering more <strong>fragrant rice</strong> than last year.</p><h2>Organic rice in focus</h2><p>Many buyers now ask for organic certification. Farmers who meet the standards can earn higher prices.</p><blockquote>Quality and traceability are what buyers ask about first.</blockquote>',
 'https://picsum.photos/seed/ponleu03/800/450', 'PUBLISHED', @admin, @cat_business,
 DATE_SUB(NOW(), INTERVAL 2 DAY), 214, 1, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 4
('Small Cafés Adopt QR Payments as Cash Use Falls',
 'small-cafes-adopt-qr-payments-cash-use-falls',
 'More small cafés now accept QR code payments, saying customers carry less cash than before.',
 '<p>From street-side stalls to small coffee shops, <strong>QR payments</strong> are becoming the normal way to pay.</p><h2>What owners say</h2><ul><li>Faster service at busy times</li><li>No need to keep small change</li><li>Easier daily sales records</li></ul>',
 'https://picsum.photos/seed/ponleu04/800/450', 'PUBLISHED', @author, @cat_business,
 DATE_SUB(NOW(), INTERVAL 9 DAY), 133, 1, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),

-- 5
('New Coding Clubs Open in 15 Provincial High Schools',
 'new-coding-clubs-15-provincial-high-schools',
 'After-school coding clubs give students in the provinces their first chance to learn programming.',
 '<p>Fifteen high schools outside the capital have opened <strong>coding clubs</strong> that meet twice a week after classes.</p><h2>What students learn</h2><p>Students start with simple web pages and move on to small games and apps.</p>',
 'https://picsum.photos/seed/ponleu05/800/450', 'PUBLISHED', @author, @cat_education,
 DATE_SUB(NOW(), INTERVAL 3 DAY), 98, 1, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- 6
('University Students Launch Free Weekend English Classes',
 'university-students-free-weekend-english-classes',
 'Volunteer students teach English to children in their neighbourhood every Saturday morning.',
 '<p>A group of university students now runs <strong>free English classes</strong> for children aged 8 to 14 every Saturday.</p><p>The classes focus on speaking and listening through games and songs.</p>',
 'https://picsum.photos/seed/ponleu06/800/450', 'PUBLISHED', @author, @cat_education,
 DATE_SUB(NOW(), INTERVAL 12 DAY), 64, 1, NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),

-- 7
('Mobile Clinics Bring Eye Checks to Rural Villages',
 'mobile-clinics-eye-checks-rural-villages',
 'A team of eye doctors visits remote villages to check eyesight and give free glasses to those in need.',
 '<p>Mobile eye clinics have visited more than 20 villages this month, offering <strong>free eye checks</strong>.</p><h2>Free glasses</h2><p>People who need glasses receive them on the same day. Serious cases are referred to provincial hospitals.</p>',
 'https://picsum.photos/seed/ponleu07/800/450', 'PUBLISHED', @admin, @cat_health,
 DATE_SUB(NOW(), INTERVAL 5 DAY), 176, 1, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),

-- 8
('Doctors Urge Families to Drink More Water During Hot Season',
 'doctors-urge-families-drink-more-water-hot-season',
 'Health workers share simple tips to avoid heat exhaustion as temperatures rise.',
 '<p>With temperatures above 38°C, doctors remind families to <strong>drink water regularly</strong>, even when not thirsty.</p><ul><li>Avoid direct sun between 11:00 and 15:00</li><li>Wear light clothes and a hat</li><li>Check on elderly neighbours</li></ul>',
 'https://picsum.photos/seed/ponleu08/800/450', 'PUBLISHED', @author, @cat_health,
 DATE_SUB(NOW(), INTERVAL 15 DAY), 241, 1, NULL, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),

-- 9
('Local Football Academy Sends Three Players to Regional Trials',
 'local-football-academy-three-players-regional-trials',
 'Three young players from a community football academy have been invited to regional youth trials.',
 '<p>A community <strong>football academy</strong> celebrates as three of its players, all aged 16, travel to regional youth trials next month.</p><p>Coaches say hard training and support from families made the difference.</p>',
 'https://picsum.photos/seed/ponleu09/800/450', 'PUBLISHED', @author, @cat_sports,
 DATE_SUB(NOW(), INTERVAL 6 DAY), 119, 1, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),

-- 10 (ARCHIVED: old event)
('Thousands Join Morning Fun Run Along the Riverside',
 'thousands-join-morning-fun-run-riverside',
 'Runners of all ages joined a 5 km fun run to raise money for a children''s hospital.',
 '<p>More than 3,000 people joined a <strong>5 km fun run</strong> along the riverside early on Sunday morning.</p><p>All money raised goes to a children''s hospital.</p>',
 'https://picsum.photos/seed/ponleu10/800/450', 'ARCHIVED', @author, @cat_sports,
 DATE_SUB(NOW(), INTERVAL 28 DAY), 302, 1, NULL, DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),

-- 11
('Young Filmmakers Showcase Short Films at Community Festival',
 'young-filmmakers-short-films-community-festival',
 'Twelve short films made by young directors were screened for free at an open-air festival.',
 '<p>An open-air <strong>film festival</strong> showed twelve short films made by directors under 25.</p><h2>Audience favourite</h2><p>A short comedy about a family tuk-tuk business won the audience award.</p>',
 'https://picsum.photos/seed/ponleu11/800/450', 'PUBLISHED', @author, @cat_entertainment,
 DATE_SUB(NOW(), INTERVAL 7 DAY), 145, 1, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),

-- 12
('Traditional Dance Troupe Prepares for International Tour',
 'traditional-dance-troupe-international-tour',
 'A classical dance group rehearses daily before performing in four countries next year.',
 '<p>A <strong>classical dance troupe</strong> of 25 dancers is rehearsing every day for its first international tour.</p><p>The group will perform in four countries and run workshops for students abroad.</p>',
 'https://picsum.photos/seed/ponleu12/800/450', 'PUBLISHED', @admin, @cat_entertainment,
 DATE_SUB(NOW(), INTERVAL 18 DAY), 92, 1, NULL, DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY)),

-- 13
('Neighbourhood Volunteers Clean Up Canal Before Rainy Season',
 'neighbourhood-volunteers-clean-up-canal-rainy-season',
 'Residents removed plastic waste from a local canal to help prevent flooding.',
 '<p>About 200 residents spent Saturday morning <strong>cleaning a local canal</strong>, removing more than two tonnes of waste.</p><p>Organisers plan to repeat the clean-up every month.</p>',
 'https://picsum.photos/seed/ponleu13/800/450', 'PUBLISHED', @author, @cat_society,
 DATE_SUB(NOW(), INTERVAL 8 DAY), 77, 1, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),

-- 14
('Restored Colonial-Era Building Reopens as Public Library',
 'restored-colonial-era-building-reopens-public-library',
 'After two years of restoration, a 100-year-old building opens its doors as a library and reading space.',
 '<p>A <strong>century-old building</strong> has reopened after two years of careful restoration, now serving as a public library.</p><h2>Keeping the past</h2><p>Original tiles, wooden shutters and staircases were repaired instead of replaced.</p>',
 'https://picsum.photos/seed/ponleu14/800/450', 'PUBLISHED', @admin, @cat_society,
 DATE_SUB(NOW(), INTERVAL 25 DAY), 188, 1, NULL, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),

-- 15 (Khmer)
('សហគមន៍ដាំដើមឈើចំនួន ១០០០ ដើមនៅតាមដងផ្លូវ',
 'community-plants-1000-trees-along-roads',
 'អ្នកស្ម័គ្រចិត្ត និងសិស្សានុសិស្សបានរួមគ្នាដាំដើមឈើ ដើម្បីបង្កើនម្លប់ និងធ្វើឱ្យទីក្រុងកាន់តែបៃតង។',
 '<p>កាលពីចុងសប្តាហ៍មុន អ្នកស្ម័គ្រចិត្តជាង ៣០០ នាក់ បានចូលរួមដាំដើមឈើចំនួន ១០០០ ដើម នៅតាមដងផ្លូវធំៗ។</p><h2>គោលបំណង</h2><p>គម្រោងនេះមានគោលបំណងបង្កើនម្លប់ កាត់បន្ថយកម្តៅ និងលើកកម្ពស់ការយល់ដឹងអំពីបរិស្ថាន។</p>',
 'https://picsum.photos/seed/ponleu15/800/450', 'PUBLISHED', @author, @cat_society,
 DATE_SUB(NOW(), INTERVAL 2 DAY), 58, 1, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 16 (Khmer)
('បណ្ណាល័យចល័តនាំសៀវភៅទៅដល់កុមារនៅជនបទ',
 'mobile-library-brings-books-rural-children',
 'ឡានបណ្ណាល័យចល័តធ្វើដំណើរទៅកាន់ភូមិនានា ដើម្បីឱ្យកុមារមានឱកាសអានសៀវភៅដោយឥតគិតថ្លៃ។',
 '<p>ឡានបណ្ណាល័យចល័តមួយគ្រឿង ដែលមានសៀវភៅជាង ២០០០ ក្បាល បានចាប់ផ្តើមធ្វើដំណើរទៅកាន់សាលារៀននៅជនបទ។</p><ul><li>បើកជារៀងរាល់ថ្ងៃសៅរ៍</li><li>ខ្ចីសៀវភៅដោយឥតគិតថ្លៃ</li><li>មានកម្មវិធីអានរឿងសម្រាប់កុមារ</li></ul>',
 'https://picsum.photos/seed/ponleu16/800/450', 'PUBLISHED', @author, @cat_education,
 DATE_SUB(NOW(), INTERVAL 10 DAY), 71, 1, NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),

-- 17 (PENDING)
('Electric Tuk-Tuks Begin Test Service in Siem Reap',
 'electric-tuk-tuks-test-service-siem-reap',
 'Twenty electric tuk-tuks start a three-month trial to test charging, range and passenger interest.',
 '<p>Twenty <strong>electric tuk-tuks</strong> have started a three-month test service around the city.</p><h2>What will be tested</h2><ul><li>Battery range on a full day of work</li><li>Charging time at shared stations</li><li>Driver income compared with petrol tuk-tuks</li></ul>',
 'https://picsum.photos/seed/ponleu17/800/450', 'PENDING', @author, @cat_technology,
 NULL, 0, 1, NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),

-- 18 (PENDING)
('Hospital Opens Online Booking to Cut Waiting Times',
 'hospital-opens-online-booking-cut-waiting-times',
 'Patients can now book a time slot online instead of queuing from early morning.',
 '<p>A provincial hospital now lets patients <strong>book appointments online</strong> or by phone.</p><p>Staff hope the system will end long morning queues at the entrance.</p>',
 'https://picsum.photos/seed/ponleu18/800/450', 'PENDING', @author, @cat_health,
 NULL, 0, 1, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 19 (DRAFT)
('Night Market Vendors Plan New Weekend Food Street',
 'night-market-vendors-new-weekend-food-street',
 'Vendors are working with local officials on a car-free food street open on Friday and Saturday nights.',
 '<p>Night market vendors are planning a <strong>weekend food street</strong> that would close one road to cars on Friday and Saturday nights.</p><p>Details are still being discussed.</p>',
 'https://picsum.photos/seed/ponleu19/800/450', 'DRAFT', @author, @cat_business,
 NULL, 0, 1, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 20 (REJECTED)
('Unconfirmed Reports of Major Changes at City Football Club',
 'unconfirmed-reports-major-changes-city-football-club',
 'Fans discuss online rumours about a new coach and several player transfers.',
 '<p>Rumours on social media suggest that a city football club may soon announce a <strong>new coach</strong> and several transfers.</p><p>The club has not commented.</p>',
 'https://picsum.photos/seed/ponleu20/800/450', 'REJECTED', @author, @cat_sports,
 NULL, 0, 1, 'The sources are not confirmed. Please add at least two named sources or an official statement from the club.',
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ---------------------------------------------------------------------
-- TAGS (looked up by slug; tags that don't exist are simply skipped)
-- ---------------------------------------------------------------------
INSERT INTO news_tags (news_id, tag_id)
SELECT n.id, t.id
FROM (
  SELECT 'cambodian-startups-ai-chatbots-khmer-customer-service' AS news_slug, 'innovation' AS tag_slug UNION ALL
  SELECT 'cambodian-startups-ai-chatbots-khmer-customer-service', 'economy' UNION ALL
  SELECT 'public-libraries-free-3d-printing-workshops-teens', 'innovation' UNION ALL
  SELECT 'public-libraries-free-3d-printing-workshops-teens', 'local' UNION ALL
  SELECT 'rice-exporters-strong-demand-european-buyers', 'economy' UNION ALL
  SELECT 'small-cafes-adopt-qr-payments-cash-use-falls', 'economy' UNION ALL
  SELECT 'small-cafes-adopt-qr-payments-cash-use-falls', 'innovation' UNION ALL
  SELECT 'new-coding-clubs-15-provincial-high-schools', 'innovation' UNION ALL
  SELECT 'new-coding-clubs-15-provincial-high-schools', 'local' UNION ALL
  SELECT 'university-students-free-weekend-english-classes', 'local' UNION ALL
  SELECT 'mobile-clinics-eye-checks-rural-villages', 'local' UNION ALL
  SELECT 'doctors-urge-families-drink-more-water-hot-season', 'breaking-news' UNION ALL
  SELECT 'local-football-academy-three-players-regional-trials', 'local' UNION ALL
  SELECT 'thousands-join-morning-fun-run-riverside', 'local' UNION ALL
  SELECT 'young-filmmakers-short-films-community-festival', 'culture' UNION ALL
  SELECT 'traditional-dance-troupe-international-tour', 'culture' UNION ALL
  SELECT 'neighbourhood-volunteers-clean-up-canal-rainy-season', 'local' UNION ALL
  SELECT 'restored-colonial-era-building-reopens-public-library', 'culture' UNION ALL
  SELECT 'community-plants-1000-trees-along-roads', 'local' UNION ALL
  SELECT 'mobile-library-brings-books-rural-children', 'culture' UNION ALL
  SELECT 'electric-tuk-tuks-test-service-siem-reap', 'innovation' UNION ALL
  SELECT 'hospital-opens-online-booking-cut-waiting-times', 'innovation' UNION ALL
  SELECT 'night-market-vendors-new-weekend-food-street', 'economy' UNION ALL
  SELECT 'unconfirmed-reports-major-changes-city-football-club', 'politics'
) m
JOIN news n ON n.slug = m.news_slug
JOIN tags t ON t.slug = m.tag_slug;

-- ---------------------------------------------------------------------
-- STATUS HISTORY (so the history timeline is not empty)
-- ---------------------------------------------------------------------
-- 1) "Created as DRAFT" for all 20 articles
INSERT INTO news_status_history (news_id, from_status, to_status, changed_by, note, changed_at)
SELECT n.id, NULL, 'DRAFT', n.author_id, 'Article created', n.created_at
FROM news n
WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%';

-- 2) Submitted for review (everything except the draft)
INSERT INTO news_status_history (news_id, from_status, to_status, changed_by, note, changed_at)
SELECT n.id, 'DRAFT', 'PENDING', n.author_id, 'Submitted for review',
       DATE_ADD(n.created_at, INTERVAL 1 HOUR)
FROM news n
WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%' AND n.status <> 'DRAFT';

-- 3) Published by the editor (published + archived)
INSERT INTO news_status_history (news_id, from_status, to_status, changed_by, note, changed_at)
SELECT n.id, 'PENDING', 'PUBLISHED', COALESCE(@editor, @admin), 'Published', n.published_at
FROM news n
WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%' AND n.status IN ('PUBLISHED', 'ARCHIVED');

-- 4) Archived
INSERT INTO news_status_history (news_id, from_status, to_status, changed_by, note, changed_at)
SELECT n.id, 'PUBLISHED', 'ARCHIVED', COALESCE(@editor, @admin), 'Event is over', n.updated_at
FROM news n
WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%' AND n.status = 'ARCHIVED';

-- 5) Rejected
INSERT INTO news_status_history (news_id, from_status, to_status, changed_by, note, changed_at)
SELECT n.id, 'PENDING', 'REJECTED', COALESCE(@editor, @admin), n.rejection_reason, n.updated_at
FROM news n
WHERE n.thumbnail LIKE 'https://picsum.photos/seed/ponleu%' AND n.status = 'REJECTED';
