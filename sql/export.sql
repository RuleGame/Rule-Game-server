use game;
select * into outfile '/var/lib/mysql-files/tmp-PlayerInfo.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.PlayerInfo;
select * into outfile '/var/lib/mysql-files/tmp-Episode.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.Episode;


