


#-- original path, on RU laptop
# /usr/lib/lightdm/lightdm:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games 



setenv PATH ~/bin:/Users/vmenkov/ant/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin

setenv PATH ${PATH}:/usr/local/opt/mysql/bin
setenv PATH ${PATH}:/Applications/Emacs.app/Contents/MacOS

#-- trying to use openjdk instead of the pre-installed Java
setenv JAVA_HOME /usr/local/opt/openjdk
setenv PATH ${JAVA_HOME}/bin:${PATH}

# This prevents programs from dumping a large file called "core"
# in the current directory when they crash.
# If you really do want these core files, delete this line.

#limit coredumpsize 0
#set ignoreeof

# Aliases:
alias dir "/bin/ls -l"
alias ls "/bin/ls -F"
alias cp "cp -p"
alias cls clear
#alias me "emacs -nw"
alias give "head \!* |  tail -5"

#alias em "emacs \!* -name \!* >& /dev/null &"
#alias em2 "setenv GDK_NATIVE_WINDOWS 1 ; emacs  \!* >& /dev/null &"

alias findfile "find . -name \!* -print"
alias ff findfile
alias clean "rm *~ *.o *.aux *.bak"
alias recent "ls -lt \!* |head -10"






