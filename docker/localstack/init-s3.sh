#!/bin/bash
# Runs inside the LocalStack container once it is ready.
# Creates the private bucket that stores inspection photos (NFR-05: bucket stays private;
# clients only access objects through short-lived presigned URLs).
set -euo pipefail

awslocal s3 mb s3://sitecheck-photos
echo "Created S3 bucket: sitecheck-photos"
