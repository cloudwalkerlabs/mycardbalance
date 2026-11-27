-- Version 1 fixture from the exported Room schema. Keep unchanged to test upgrades.
CREATE TABLE IF NOT EXISTS `card` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nickname` TEXT NOT NULL, `last4` TEXT NOT NULL, `network` TEXT NOT NULL, `currency` TEXT NOT NULL, `expiry` INTEGER NOT NULL, `created_at` INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS `card_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `card_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, FOREIGN KEY(`card_id`) REFERENCES `card`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_card_event_card_id_id` ON `card_event` (`card_id`, `id`);
CREATE TABLE IF NOT EXISTS `preference` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`));
CREATE VIEW `card_ledger` AS SELECT id, card_id, type, amount_minor, note, created_at, balance_after_minor,
               balance_after_minor - IFNULL(LAG(balance_after_minor) OVER (PARTITION BY card_id ORDER BY id), 0)
                   AS delta_minor
        FROM (
            SELECT *,
                   SUM(CASE WHEN type = 'REDEEM' THEN -amount_minor ELSE amount_minor END)
                       OVER (PARTITION BY card_id, grp ORDER BY id) AS balance_after_minor
            FROM (
                SELECT *,
                       SUM(CASE WHEN type = 'REDEEM' THEN 0 ELSE 1 END)
                           OVER (PARTITION BY card_id ORDER BY id) AS grp
                FROM card_event
            )
        );
CREATE VIEW `card_with_balance` AS SELECT card.id, card.nickname, card.last4, card.network, card.currency, card.expiry, card.created_at,
               ledger.balance_after_minor AS balance_minor,
               ledger.created_at AS last_activity_at
        FROM card
        JOIN card_ledger AS ledger ON ledger.card_id = card.id
        WHERE ledger.id = (SELECT MAX(e.id) FROM card_event AS e WHERE e.card_id = card.id);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd591790fd49de2f8742433ca80057a4e');
PRAGMA user_version = 1;
