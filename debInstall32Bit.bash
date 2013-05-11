#!/usr/bin/env bash

function die() {
  echo $*
  exit 1
}

if [ $# -ne "1" ]
then
    die "$0: Received $# args... whether or not this is a release required"
fi

test -d install/linux/lib || mkdir -p install/linux/lib || die "Could not create install/linux/lib"
cp lib/linux/x86/libunix-java.so install/linux/lib/  || die "Could not copy libunix?"
./debInstall.bash $* 32 690
rm install/linux/lib/*
