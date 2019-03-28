import sys
import subprocess
import os

# suppress the stdout 
FNULL = open(os.devnull, 'w')

# generate prebuild file
cmd = "cd python && python egt.py --query_features ../data/roxHD_query_fused_3s_cq.npy --index_features ../data/roxHD_index_fused_3s_cq.npy --query_hashes query_hashes.txt --index_hashes index_hashes.txt --Do_QE False --Num_candidates 300 --OutputFile prebuild_from_python.txt --evaluate roxford5k && cd .."
subprocess.call(['/bin/bash', '-c', cmd], stdout=FNULL)

print("finished prebuild")

# run egt
cmd = 'mvn exec:java -Dexec.mainClass="EGT" -Dexec.args="-k 250 -q 70 -t 420000 -p 5000 python/prebuild_from_python.txt test.txt"'
subprocess.call(['/bin/bash', '-c', cmd], stdout=FNULL)

print("finished EGT")

# evaluate
cmd = 'cd python && python evaluate_prebuild.py --f ../test.txt --index_hashes index_hashes.txt --num_query 70 --num_score 0 --evaluate roxford5k --skip 1 && cd ..'
subprocess.call(['/bin/bash', '-c', cmd])

