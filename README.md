<p align="center">
<a href="https://layer6.ai/"><img src="https://github.com/layer6ai-labs/DropoutNet/blob/master/logs/logobox.jpg" width="180"></a>
</p>

## CVPR2019 Explore-Exploit Graph Traversal for Image Retrieval
Authors: Cheng Chang, [Guangwei Yu](http://www.cs.toronto.edu/~guangweiyu), [Chundi Liu](https://github.com/chundiliu), [Maksims Volkovs](http://www.cs.toronto.edu/~mvolkovs) ([paper](http://www.cs.toronto.edu/~mvolkovs/cvpr2019EGT.pdf))

## Datasets and Environment
* Java 8+
* Code primarily tested for Python 3.5
* Evaluation data can be downloaded from [here](https://s3.amazonaws.com/public.layer6.ai/landmark/EGT-DATA/evaluation.tar.gz) (taken from the authors of the ROxford and RParis [datasets](  https://github.com/filipradenovic/revisitop), and redistributed with permission)


## Setup
We have included a demo script `run_all.py` to run our model end-to-end.
1. Download the evaluation data and place the `evaluation.tar.gz` file in `data/`, then decompress and untar (e.g. `tar -xzf evaluation.tar.gz`).
2. Run the demo script with `python run_all.py`. The script computes mAP accuracy for ROxford Hard and Medium datasets, and you should see output like this: `mAP H: 56.29, M: 73.63`. These correspond to results reported in the first half of Table 1 in the paper.
<p>

The demo script generates kNN prebuild file, runs our model EGT and evaluates results. The following sections describe each of these steps.

<p>

## kNN prebuild file
* EGT uses kNN prebuild file as input. `run_all.py` generates this file as part of the pipeline, and we also provide a stand-alone tool to generate it (see below). The format of the prebuild file is row-separated list of edges denoted by `<qid>` as the image id of the row, followed by pairs of `<id> <weight>` where `<id>` is the neighbor image id and `<weight>` is the edge weight:
```
<qid>,<id> <weight> <id> <weight> ... <id> <weight>
<qid>,<id> <weight> <id> <weight> ... <id> <weight>
...

<qid>,<id> <weight> <id> <weight> ... <id> <weight>
```
* Query images are placed at the top of the prebuild file. Note that query images are kept separate from index images in the kNN graph, as required by online inference (see Section 3:"Online Inference" in the paper). 
* Example stand-alone command to generate kNN prebuild file from global descriptors:
    ```
    cd python
    python egt.py --query_features ../data/roxHD_query_fused_3s_cq.npy \
        --index_features ../data/roxHD_index_fused_3s_cq.npy \
        --query_hashes query_hashes.txt --index_hashes index_hashes.txt \
        --Do_QE False --k 250 --OutputFile prebuild_from_python.txt \
        --evaluate roxford5k
    ```
    
## EGT
* For efficiency our model is implemented in Java. We have provided an executable jar that you can run. The executable takes kNN prebuild file as input, and outputs the final retrieval ranking in a similar format:
```
<qid>,<id> <id> ... <id>
<qid>,<id> <id> ... <id>
...
<qid>,<id> <id> ... <id>
```
* As in the paper, EGT has the following hyper-parameters: number of neighbors in kNN graph `k`, threshold for explore/exploit tradeoff `t`, and output list size `p`.

* Example to generate the paper result from a prebuild file:
    ```
    java -jar target/egt.jar -k 250 -q 70 -t 0.42 -p 5000 \
    python/prebuild_from_python.txt test.txt
    ```
     
* For research and development on top of EGT see `src/main/java/EGT.java`.
    
* To create executable jar, compile with `mvn clean compile assembly:single`

## Evaluation

* The evaluation script takes as input the EGT output file or kNN prebuild file, and computes mAP on medium and hard subsets of ROxford or RParis datasets. For more information, see help in `python/evaluate_prebuild.py`. Example call to invoke the evaluation is:

    ```
    python evaluate_prebuild.py --f ../test.txt \
        --index_hashes index_hashes.txt --num_query 70 \
        --evaluate roxford5k
    ```
