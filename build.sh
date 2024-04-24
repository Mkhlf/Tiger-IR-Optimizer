#!/bin/bash

# Write a script to build your optimizer in this file 
# (As required by your chosen optimizer language)

mkdir build 
find materials/src -name "*.java" > sources.txt
javac -d build @sources.txt