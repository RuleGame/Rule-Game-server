#!/usr/bin/perl

use strict;

#-------------------------------------------------------------------------------
# Joins lines from the file produced by count-moves.pl, computing various
# aggregate values for each rule set.
#-------------------------------------------------------------------------------
# cd home/vmenkov/gemini-play
#
# /home/vmenkov/w2020/game/gemini-sample-scripts/count-moves.pl --long > all-moves-long.csv
# /home/vmenkov/w2020/game/gemini-sample-scripts/join-count-moves.pl  all-moves-long.csv > joined.csv
#-------------------------------------------------------------------------------
    
# cat  fc_oc24_combined_v2.csv | perl -pe 's/\b[ADI]+\b/MMM/g'
# ...
# runName,algorithm,rule_set,rule_set_conditions,
#    threshold_mean,window_mean,threshold_max,window_max,e_star_mean,e_star_mean_lo,e_star_mean_hi,e_star_mean_vals,e_star_max,e_star_max_lo,e_star_max_hi,e_star_max_vals,min_error,min_window_error_mean,min_window_error_lo,min_window_error_hi,min_window_errors,
#     move_logs,good_move_length,m_star_values,M_star,alg_parameter

# FC_a2c_Transformer_cm_RBKY,a2c_Transformer,cm_RBKY,no predecessors,
#    0.25,10,0.25,5,426,405,469,"[457, 407, 426, 469, 405]",440,403,584,"[579, 426, 440, 584, 403]",0.2277731092436974,440,422,470,"[458, 440, 426, 470, 422]",
#    MMM ; MMM ; MMM ; MMM ; MMM,10,"[9527, 9136, 4976, 11010, 10350]",9527.0,https://drive.google.com/file/d/1suVeXNIgino_XvQn-MVVX8AWfoDsPQxL/view?usp=drivesdk
#-------------------------------------------------------------------------------

use Getopt::Long;

#-- With the --semicolon option, only use semicolons inside fields, so that
#-- "cut -d , " can be later used to separate fields
my $semicolon           = undef;
GetOptions ('semicolon' => \$semicolon);

my $big           = 100;
GetOptions ('big=s' => \$big);

my ($inFile) = @ARGV;

my @lines = `cat $inFile`;

my $header = shift @lines;

defined($header) or die "No data in $inFile\n";

$header =~ s/\s+$//;
$header =~ s/^#//;
my @cols = split(/,/, $header);

#-- maps rule name to a ref to hash table
my %h = ();


# $header = "#path,rule,won,trainEpisodes,trainMoves,testBoards,move_logs";
my $lineNo = 0;
foreach my $line (@lines) {
    $lineNo++;
    $line =~ s/\s+$//;
    my @q = split(/,/, $line);
    scalar(@q) == scalar(@cols) or die "Column count mismatch in line $lineNo: $line\n";
    my %g = ();    
    foreach my $j (0..$#cols) {
	$g{ $cols[$j] } = $q[$j];
    }
    my $rule = $g{"rule"};
    exists($h{$rule}) or $h{$rule} = [];
    push @{$h{$rule}}, \%g;
}


my @outCols = qw( runName algorithm rule_set rule_set_conditions
		  move_logs good_move_length m_star_values M_star M_harmonic
		  alg_parameter
		  good_test_boards all_test_boards accuracy_on_test_boards );

my $targetStreak = 10;

my @rules = sort(keys %h);

print join(",", @outCols) . "\n";

foreach my $rule(@rules) {
    my @tables = @{$h{$rule}};
    @tables = sort{ my %ga = %{$a}; my %gb = %{$b}; $ga{"path"} cmp $gb{"path"}} @tables;
#    print "Rule $rule: ". scalar(@tables). " runs\n";
    my @mm = ();
    my $good=0;
    my $all=0;
    my $sumInv = 0;
    my $hasZeroM = 0;
    foreach my $rt (@tables) {
	my %g = %{$rt};
	my $won = $g{"won"};
	my $s = $g{"testBoards"};
	$s=~ m|(\d+)/(\d+)| or die "Cannot parse ratio $s\n";
	$good += $1;
	$all += $2;
	my $ms = $won? $g{"trainMoves"} - $targetStreak : $big;
	push @mm, $ms;
	if ($ms == 0) {
	    $hasZeroM = 0;
	} else {
	    $sumInv += 1.0/$ms;
	}
    }
    my $harmonic = $hasZeroM? 0: scalar(@tables)/$sumInv;
	
    my $param = "";
    if ($rule =~ /^(t\d+-\d+)-(.*)/) {
	($param,$rule) = ($1,$2);
    } else {
	$param = "t9-9";
    }


# runName algorithm rule_set rule_set_conditions
# move_logs good_move_length m_star_values M_star alg_parameter
# good_test_boards all_test_boards accuracy_on_test_boards    

    #-- always semicolon, per Christo
    my $moveLogs = join(";", map{ ${$_}{"move_logs"}} @tables);
    #-- either comma per Christo, or semicolon for "cut -d ," convenience
    my $sep = $semicolon? ";" : ",";

    my %out = (	"runName" => "playStateless",
		"algorithm" => "G3F",
		"rule_set" => $rule,
		"rule_set_conditions" => "",
		"move_logs" => $moveLogs,
		"good_move_length" => $targetStreak,
		"m_star_values" => "[". join($sep, @mm) . "]",
		"M_star" => &median(\@mm),
		"M_harmonic" => $harmonic,
		"alg_parameter" => $param,
		"good_test_boards" => $good,
		"all_test_boards" => $all,
		"accuracy_on_test_boards" => $good/$all     );

    my @vals = map{ my $x = %out{$_}; $x =~ /,/? '"'.$x.'"' : $x; } @outCols;
    print join(",", @vals) . "\n";
}



sub median($) {
    my ($pa) = @_;
    my @a = @{$pa};
    @a = sort { $a <=> $b} @a;
    my $n = scalar @a;
    return ($n % 2 ==0) ? 0.5*( $a[$n/2-1] + $a[$n/2]) : $a[int($n/2)];
}
