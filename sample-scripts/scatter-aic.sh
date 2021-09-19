#!/bin/csh

echo 'set xlabel "AIC flat"'>tmp.gnu
echo 'set xlabel "AIC random"' >> tmp.gnu

foreach rules (`cut -d , -f 1 summary-flat.csv | sort | uniq | grep -v ruleSetName`)
echo Rules=$rules
set p=`echo $rules | sed -e 's|/|-|g'`
grep $rules summary-flat.csv | cut -d , -f 14 > aic-flat-$p.csv
grep $rules summary-p0-COMPLETELY_RANDOM.csv | cut -d , -f 14 > aic-random-$p.csv
paste  aic-flat-$p.csv  aic-random-$p.csv > aic-scatter-$p.dat


echo "set term x11" >> tmp.gnu
echo "set title 'Rules: $rules'" >> tmp.gnu
echo "plot 'aic-scatter-$p.dat' with points;" >> tmp.gnu
echo "set out 'aic-scatter-$p.png'" >> tmp.gnu
echo "set term png" >> tmp.gnu
echo "replot" >> tmp.gnu

end


