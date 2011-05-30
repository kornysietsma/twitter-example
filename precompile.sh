#!/bin/bash
set -e
coffee -c -o resources/public/javascript/ views/coffee/
sass --update views/scss:resources/public/stylesheets
