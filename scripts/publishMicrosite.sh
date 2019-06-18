#!/bin/bash
set -e

git config --global user.email "katrix97@hotmail.com"
git config --global user.name "Katrix"
git config --global push.default simple

sbt docs/publishMicrosite