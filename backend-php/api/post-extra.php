<?php

// This script is called from the Hauk app to push location updates to the
// server. Each update contains a location and timestamp from when the location
// was fetched by the client.

include("../include/inc.php");
header("X-Hauk-Version: ".BACKEND_VERSION);

requirePOST(
    "sid"   // Session ID to post to.
);

$memcache = memConnect();

// Retrieve the session data from memcached.
$sid = $_POST["sid"];
$session = new Client($memcache, $sid);
if (!$session->exists()) die($LANG['session_expired']."\n");

if (isset($_POST["audioMeta"])) {
    $session->setAudioMeta($_POST["audioMeta"]);
}

if (isset($_POST["arrival"])) {
    $session->setArrival($_POST["arrival"]);
}

if (isset($_POST["target"])) {
    $session->setTarget($_POST["target"]);
}

if (isset($_POST["nextTurn"])) {
    $session->setNextTurn($_POST["nextTurn"]);
}

if (isset($_POST["nextTurnDst"])) {
    $session->setNextTurnDst($_POST["nextTurnDst"]);
}

if (isset($_POST["nextTurnIcon"])) {
    $session->setNextTurnIcon($_POST["nextTurnIcon"]);
}

$session->save();

if ($session->hasExpired()) {
    echo $LANG['session_expired']."\n";
} else {
    echo "OK\n".getConfig("public_url")."?%s\n".implode(",", $session->getTargetIDs())."\n";
}
