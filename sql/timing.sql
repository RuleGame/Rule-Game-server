#-----------------------------------------------
#  Making some estimates on how much time a player spends playing
#-----------------------------------------------
drop table tmp;
CREATE TEMPORARY TABLE tmp 
select p.playerId, p.completionCode, min(p.date) 't0', max(e.endTime) 't1', to_seconds(max(e.endTime))-to_seconds(min(p.date)) 'seconds' from PlayerInfo p, Episode e where e.PLAYER_ID=p.id and p.experimentPlan like 'pilot%' and not p.playerId like 'Aria%'
group by p.playerId, p.completionCode;

select * from tmp order by seconds asc;

drop table tmp1;
CREATE TEMPORARY TABLE tmp1 
select ceiling(seconds/600)*10 'minutes', playerId, completionCode from tmp;

select minutes, count(*) 'Completers count' from tmp1 where completionCode is not null group by minutes order by minutes asc;

select minutes, count(*) 'Quitters  count' from tmp1 where completionCode is null group by minutes order by minutes asc;
