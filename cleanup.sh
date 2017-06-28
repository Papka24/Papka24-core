#!/bin/bash
id=`ps -A | grep java | awk '{print $1;}'`
kill $id
