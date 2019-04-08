<p align="center">
<a href="https://layer6.ai/"><img src="https://github.com/layer6ai-labs/DropoutNet/blob/master/logs/logo.svg" width="180"></a>
</p>

## CVPR2019 Explore-Exploit Graph Traversal for Image Retrieval
Authors: Cheng Chang, [Guangwei Yu](http://www.cs.toronto.edu/~guangweiyu), Chundi Liu, [Maksims Volkovs](http://www.cs.toronto.edu/~mvolkovs) ([paper](http://www.cs.toronto.edu/~mvolkovs/cvpr2019EGT.pdf))

## Datasets and Environment
* Java 8+
* Code primarily tested for Python 3.5
* Evaluation data can be downloaded from [here](https://s3.amazonaws.com/public.layer6.ai/landmark/EGT-DATA/evaluation.tar.gz) (taken from authors of the ROxford and RParis datasets at [here](  https://github.com/filipradenovic/revisitop), redistributed with permission from author)


# Setup
We have included end-to-end script in `run_all.py` to demonstrate EGT.
1. Download the evluation data and place the `evaluation.tar.gz` file in `data/`, then decompress and untar (e.g. `tar -xzf evaluation.tar.gz`).
2. Run graph generation, EGT, then evaluation to produce the ROxford 5k results with `run_all.py`
<p>
    
* The [graph generation](#knn-prebuild-file) produces a kNN prebuild file that describes the weighted kNN graph.
* The [EGT program](#egt) takes this as input and produces output text file of the final ranking.
* The [evaluation script](#evaluation) for ROxford and RParis is provided to generate the mAP evaluation in the paper.

<p>
The following section describes how to run kNN generation, EGT, and evaluation in detail.

# kNN prebuild file
* We provide Python script to generate the kNN graph used as input to our model. The format of the prebuild file is row separated list of edges denoted by `<qid>` as the image id of the row, followed by pairs of `<id> <weight>` where `<id>` is the neighbor image id and `<weight>` is the edge weight.
```
<qid>,<id> <weight> <id> <weight> ... <id> <weight>
<qid>,<id> <weight> <id> <weight> ... <id> <weight>
...

<qid>,<id> <weight> <id> <weight> ... <id> <weight>
```
* Query images are placed at the top of the output prebuild file. Note that query images are kept separate from index images in the kNN graph as required by online inference (see Section 3:"Online Inference" in the paper). 
* Example command to generate kNN prebuild file from global descriptors:
    ```
    cd python
    python egt.py --query_features ../data/roxHD_query_fused_3s_cq.npy \
        --index_features ../data/roxHD_index_fused_3s_cq.npy \
        --query_hashes query_hashes.txt --index_hashes index_hashes.txt \
        --Do_QE False --Num_candidates 300 --OutputFile prebuild_from_python.txt \
        --evaluate roxford5k
    ```
    
# EGT
* The proposed graph-traversal algorithm EGT is written in Java. We have provided an executable jar that you can run. The executable takes kNN prebuild file as input, and outputs the final retrieval ranking in a similar format:
```
<qid>,<id> <id> ... <id>
<qid>,<id> <id> ... <id>
...
<qid>,<id> <id> ... <id>
```
* We provide options to experiment with `k` (number of neighbors in kNN graph), `t` (threshold for explore/exploit), and `p` (output list size) as in the paper. Additionally, there is a flag to make the graph symmetric. For symmetric graph, we skip the query rows to keep the inference online by preventing the existing query and index descriptors from seeing other query descriptors as neighbors due to symmetry. Note that within the prebuild file, our script never uses other query as neighbors for any image.

* Execute EGT program jar with
` java -jar target/egt.jar`

* Example to generate the paper result from a prebuild file:
    ```
    java -jar target/egt.jar -k 250 -q 70 -t 0.42 -p 5000 \
    python/prebuild_from_python.txt test.txt
    ```
     
* For research and development on top of EGT, see `src/main/java/EGT.java` on how to execute `EGTImpl` directly.
    
* To create executable jar, compile with
     `mvn clean compile assembly:single`

## Evaluation

* To evaluate a file with one header line:

    ```
    python evaluate_prebuild.py --f ../test.txt \
        --index_hashes index_hashes.txt --num_query 70 \
        --num_score 0 --evaluate roxford5k --skip 1
    ```
