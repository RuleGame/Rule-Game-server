#!/bin/csh

#-------------------------------------------------------------------------
#-- For usage examples, please see run-pooling-sample.sh and
#-- run-pooling-sample-2.sh
#
#-- Sample usage
# 1) 
# $sc/analyze-transcripts-mwh.sh -export tmp.csv 'pk/explore_1' -precMode EveryCond
#
# This is our usual data extraction, as used back since 2023-01, for all episodes played in a specified experiment plan. The output goes to tmp.csv
#
# 2) 
# $sc/ecd.sh -import tmp.csv -target pk/position_A -alpha 0.5
#
# This builds and draws ECDF curves, and carries out the Holm-Bonferroni (HB) analysis. In this example I am using alpha=0.5 instead of the usual 0.05, in order for the HB process to select at least something.
#
# For each target rule set, 2 SVG files are produced. One file just shows the ECDF curves for this rule set, and the "median" of each one; they are color-coded for easier reading. The other file shows all curves in the same color (red), and shows green lines connecting the "medians" of the curves in each pair that was selected by the HB process as having "low similarity".
#
#-- Another sample
#
# scripts/ecd.sh  -import tmp.csv -target vb/clockwiseTwoFree  -alpha 0.05 -beta 0.5 >  vb-clockwiseTwoFree-legend.txt
#
# Arguments:
# -target taretRuleSetName : the name of a rule set the performance of the players on which, after different preceding experiences, is to be analyzed. If it is not specified, we compare all experiences, with all targets.
# -import input.csv : a CSV file (produced e.g. by analyze-transcripts-mwh.sh) containing the input data to be analyzed. You must specify at least one input file; you can have multiple -import options, for multiple input files.
#  -csvOut outputDirectoryName
# -alpha value
# -beta value
# -sim MW | KS | Min | Max  : How  the similarity (in both HB calculations and in clustering) is computed from the MW and KS p-values: using one of them, or the min or max of the two. The defaul  was Max for HB and Min for clustering.
# -simHB  MW | KS | Min | Max  : similarity measure to use in HB 
# -simClustering  MW | KS | Min | Max  : similarity measure to use in clustering
# -untie aSmallRealValue : artificial jitter 
# -seed randomNumberGeneratorSeed : for use in the untie process
# -checkSym : for debugging
# -curve false : If this option is provided, the ECD curve itself is not shown on the plot; only its median point is.
# -hbAll true: If this option is provided, we use the HB process to compare all pairs of ECD curves, rather than only comparing ECD curves against the naive curve.
# -colors red,green,purple : curve colors
# -linkColor black : HB connector color
# -labels labels.csv : a CSV file that assigns lables to rules. Format "label,rule", e.g fdcl-labels.csv in this directory
#--------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"

java edu.wisc.game.tools.pooling.Ecd $argv[1-]



