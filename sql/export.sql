use game;
select * into outfile '/var/lib/mysql-files/tmp-PlayerInfo.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.PlayerInfo;
select * into outfile '/var/lib/mysql-files/tmp-Episode.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.Episode;


#-- For Aria: mark the first bonus episode in each series
CREATE TEMPORARY TABLE z
select PLAYER_ID, seriesNo, min(id) id
from  Episode where bonus group by PLAYER_ID, seriesNo;

select e.*,
(CASE WHEN exists (select * from z where z.id = e.id) THEN 1 ELSE 0 END) as 'bonusActivate'
into outfile '/var/lib/mysql-files/tmp-Episode-2.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.Episode e;
