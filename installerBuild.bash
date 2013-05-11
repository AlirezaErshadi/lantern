#!/usr/bin/env bash

CONSTANTS_FILE=src/main/java/org/lantern/LanternClientConstants.java
VERSION_FILE=pom.xml
function die() {
  echo $*
  echo "Reverting constants file"
  git checkout -- $CONSTANTS_FILE || die "Could not revert $CONSTANTS_FILE?"
  exit 1
}

test -f ../secure/bns-osx-cert-developer-id-application.p12 || die "Need OSX signing certificate at ../secure/bns-osx-cert-developer-id-application.p12"
test -f ../secure/bns_cert.p12 || die "Need windows signing certificate at ../secure/bns_cert.p12"

javac -version 2>&1 | grep 1.7 && die "Cannot build with Java 7 due to bugs with generated class files and pac"

which install4jc || die "No install4jc on PATH -- ABORTING"
printenv | grep INSTALL4J_KEY || die "Must have INSTALL4J_KEY defined with the Install4J license key to use"
printenv | grep INSTALL4J_MAC_PASS || die "Must have OSX signing key password defined in INSTALL4J_MAC_PASS"
printenv | grep INSTALL4J_WIN_PASS || die "Must have windows signing key password defined in INSTALL4J_WIN_PASS"
test -f $CONSTANTS_FILE || die "No constants file at $CONSTANTS_FILE?? Exiting"

CURRENT_VERSION=$(grep '<version>' pom.xml |head -1|sed 's,.*<version>\(.*\)</version>,\1,')
fgrep $CURRENT_VERSION $VERSION_FILE &>/dev/null || die "CURRENT_VERSION \"$CURRENT_VERSION\" not found in pom.xml"

NEW_VERSION=$(echo $CURRENT_VERSION|sed 's/-SNAPSHOT//')
MVN_ARGS=$1
echo "*******MAVEN ARGS*******: $MVN_ARGS"
if [ $# -gt "0" ]
then
    RELEASE=$1;
else
    RELEASE=true;
fi

curBranch=`git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/\1/'`
git pull --no-rebase origin $curBranch || die '"git pull origin" failed?'
git submodule update || die "git submodule update failed!!!"

NEW_VERSION_WITH_SHA=$1-`git rev-parse HEAD | cut -c1-10`
perl -pi -e "$. < 10 && s/$CURRENT_VERSION/$NEW_VERSION/" $VERSION_FILE || die "s/$CURRENT_VERSION/$NEW_VERSION/ in pom.xml failed"

# XXX do this automatically
echo "Replaced $CURRENT_VERSION with $NEW_VERSION in pom.xml."
echo "If this is a release, you may want to manually bump"
echo "to the next -SNAPSHOT version in your next commit."

# The build script in Lantern EC2 instances sets this in the environment.
if test -z $FALLBACK_SERVER_HOST; then
    FALLBACK_SERVER_HOST="75.101.134.244";
fi
perl -pi -e "s/fallback_server_host_tok/$FALLBACK_SERVER_HOST/g" $CONSTANTS_FILE || die "Could not set fallback server host"

# The build script in Lantern EC2 instances sets this in the environment.
if test -z $FALLBACK_SERVER_PORT; then
    FALLBACK_SERVER_PORT="7777";
fi
perl -pi -e "s/fallback_server_port_tok/$FALLBACK_SERVER_PORT/g" $CONSTANTS_FILE || die "Could not set fallback server port";

GE_API_KEY=`cat lantern_getexceptional.txt`
if [ ! -n "$GE_API_KEY" ]
  then
  die "No API key!!" 
fi

perl -pi -e "s/ExceptionalUtils.NO_OP_KEY/\"$GE_API_KEY\"/g" $CONSTANTS_FILE

mvn clean || die "Could not clean?"
mvn $MVN_ARGS install -Dmaven.test.skip=true || die "Could not build?"

echo "Reverting constants file"
git checkout -- $CONSTANTS_FILE || die "Could not revert version file?"

echo "Reverting version file"
git checkout -- $VERSION_FILE || die "Could not revert version file?"

cp target/lantern-$NEW_VERSION.jar install/common/lantern.jar || die "Could not copy jar?"

./bin/searchForJava7ClassFiles.bash install/common/lantern.jar || die "Found java 7 class files in build!!"
if $RELEASE ; then
    echo "Tagging...";
    git tag -f -a v$NEW_VERSION -m "Version $NEW_VERSION_WITH_SHA release with MVN_ARGS $MVN_ARGS";

    echo "Pushing tags...";
    git push --tags || die "Could not push tags!!";
    echo "Finished push...";
fi

install4jc -L $INSTALL4J_KEY || die "Could not update license information?"
