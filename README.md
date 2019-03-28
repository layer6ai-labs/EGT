# Explore-Exploit Graph Traversal for Image Retrieval
Code for CVPR2019 paper: Explore-Exploit Graph Traversal for Image Retrieval

## Prerequisite
* EGT requires java 1.8
* python 3.5 for kNN graph generation and evaluation
* For ROxford and RParis, please obtain dataset and evaluation code using https://github.com/filipradenovic/revisitop
* Download from https://s3.amazonaws.com/public.layer6.ai/landmark/EGT-DATA/evaluation.tar.gz to get the evaluation data

## Get Started

Run graph generation, EGT, then evaluation to produce the ROxford 5k results with `run_all.py`


## kNN "prebuild" file

* Example to generate a "prebuild" file from embedding:
    ```
    cd python
    python egt.py --query_features ../data/roxHD_query_fused_3s_cq.npy \
        --index_features ../data/roxHD_index_fused_3s_cq.npy \
        --query_hashes query_hashes.txt --index_hashes index_hashes.txt \
        --Do_QE False --Num_candidates 300 --OutputFile prebuild_from_python.txt \
        --evaluate roxford5k
    ```
    
## EGT

* Execute EGT program jar with
` java -jar target/egt.jar`

* Example to generate the paper result from a prebuild file:
    ```
    java -jar target/egt.jar -k 250 -q 70 -t 420000 -p 5000 \
    python/prebuild_from_python.txt test.txt
    ```
    
* To create executable jar, compile with
     `mvn clean compile assembly:single`
* For research and development on top of EGT, see `src/main/java/EGT.java` on how to execute `EGTImpl` directly.

## Evaluation

* To evaluate a file with one header line:

    ```
    python evaluate_prebuild.py --f ../test.txt \
        --index_hashes index_hashes.txt --num_query 70 \
        --num_score 0 --evaluate roxford5k --skip 1
    ```
