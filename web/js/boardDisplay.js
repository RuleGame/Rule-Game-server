"use strict";

//---------------------------------------------------------
// This script is to be included into HTML pages generated by
// HtmlDisplay.java (used by GameService2Html.java), to activate
// buttons in HTML board display
//---------------------------------------------------------

// this function is used whenever one clicks on a game piece
function selectXY_old(x,y)	{

    const fx = document.getElementById("moveFormX");
    const fy = document.getElementById("moveFormY");
    fx.value= x;
    fy.value=y;
}

function selectPiece(id)	{

    const fid = document.getElementById("moveFormId");
    fid.value= id;
}

//-- Deprecated, from GS 7.*
function doBucket_old(bx,by) {
    const fx = document.getElementById("moveFormX");
    const fy = document.getElementById("moveFormY");
    const gx = document.getElementById("moveFormBX");
    const gy = document.getElementById("moveFormBY");
    gx.value = bx;
    gy.value = by;
    if (fx.value.trim()=='' || fy.value.trim()=='') {
	window.alert("No use to click on a bucket before a game piece has been selected!");
    } else {
	//window.alert("Will submit");
	document.getElementById("moveForm").submit();  
    }
}

//-- For GS 8.0
function doBucket(bid) {
    const fid = document.getElementById("moveFormId");
    const fbid = document.getElementById("moveFormBid");

    fbid.value = bid;

    if (fid.value.trim()=='' || fid.value.trim()=='') {
	window.alert("No use to click on a bucket before a game piece has been selected!");
    } else {
	//window.alert("Will submit");
	document.getElementById("moveForm").submit();  
    }
}

