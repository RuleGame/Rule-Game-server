drop table tmp;
CREATE TEMPORARY TABLE tmp
select ea.episodeId id, min(ea.endTime) t0, min(eb.endTime) t1
from Episode ea, Episode eb, PlayerInfo p
where ea.PLAYER_ID=p.id and eb.PLAYER_ID=p.id
and ea.endTime is not null and eb.endTime is not null
and eb.endTime > ea.endTime
and p.experimentPlan like 'pilot%' and not p.playerId like 'Aria%'
group by ea.episodeId, ea.startTime;

insert into tmp
select p.playerId id, min(p.date) t0, min(eb.endTime) t1
from Episode eb, PlayerInfo p
where  eb.PLAYER_ID=p.id
and eb.endTime is not null
and p.experimentPlan like 'pilot%' and not p.playerId like 'Aria%'
group by p.playerId, p.date;

drop table tmp1;
CREATE TEMPORARY TABLE tmp1 
select  id,  to_seconds(t1)-to_seconds(t0) 'seconds'
from tmp;

drop table tmp2;
CREATE TEMPORARY TABLE tmp2
select id, ceiling(seconds/60) minutes from tmp1;

select minutes, count(*) 'Episodes count' from tmp2 group by minutes order by minutes asc;
