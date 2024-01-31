#!/bin/bash

#------------------------------------------------------------------
# This script sits at a remote host  running an instance of Game Server
# (e.g. a Plesk host),
# and is used to pack all the transcript files etc into a single zip file,
# for later transmission to the analysis host.
# Normally, this script (on the remote host) is invoked via ssh from the
# analysis host, when pull-files.sh or pull-mysql.sh is executed there.
#
# In a more normal environment, no such script would be needed, since
# we could simply run rsync, which is a lot more efficient. However,
# Plesk hosts have a chrooted shell, which does not support rsync.
# ------------------------------------------------------------------

t=tmp.zip

if [ -e $t ]
then 
    rm $t
else
    echo "There is no previous $t to remove"
fi
zip -r $t /opt/w2020/saved
ls -l $t
