#TODO: example of complete pipeline in python with pre-build file generation and revop evaluation
import numpy as np
import argparse

def QE(Q, X, k=5, Skip_self=False):
    # Do query expansion for Q with top k in X
    # Q: Query features, with shape = (feature_dim, num_queries)
    # X: Index features, with shape = (feature_dim, num_index)
    # skip_self: for Q=X, and want to skip self during query expansion

    sim = np.matmul(X.T, Q)
    #if Skip_self:
    #    k += 1 #get the next one
    sim_top = np.argpartition(sim, -k, 0)[-k:, :]
    Qexp = np.array([(np.sum(X[:,top[:k]],axis=1)+query) for query,top in zip(Q.T, sim_top.T)]).T

    # if skip_first, assume one of candidate is equal to self, so remove it
    #if Skip_self:
    #    Qexp -= Q
    Qexp = Qexp / np.linalg.norm(Qexp,ord=2, axis=0)
    return Qexp


def generate_prebuild(Q_features, X_features, Q_hashes, X_hashes, Do_QE, QE_topN, Num_candidates, evaluate, OutputFile):
    # Q_features: Query features, Numpy array of shape=(feature_dim, num_queries)
    # X_features: index features, Numpy array of shape=(feature_dim, num_index)
    # Q_hashes: query hashes to write in output File. list of size num_queries
    # X_hashes: index hashes to write in output File. list of size num_index
    # Do_QE: Boolean, whether to do qe and DBA
    # QE_topN: Integer, Do QE with topK Neighbours, for both DBA and QE
    # Num_candidates: Integer, number of neighbours with scores to output
    # outputFile: String, output File
    
    # Perform QE if needed
    if Do_QE:
        Q_features = QE(Q_features, X_features, QE_topN, False)
        X_features = QE(X_features, X_features, QE_topN, True)

    # Concatenate Query and Index and do retrieval at once
    f = np.concatenate([Q_features, X_features], axis=1)
    sim = np.matmul(X_features.T, f)
    sim_top = np.argsort(-sim, axis=0) # need order, so not argpartition, but argsort
    
    if evaluate is not None:
        from revop import init_revop, eval_revop
        cfg = init_revop(evaluate, "../data/evaluation/data/")
        score = eval_revop(sim_top[:Num_candidates, :Q_features.shape[1]]) # downsize it to be the same as the prebuild file
        print("Map H: {:.2f}".format(score))

    # write them into the output file
    print("Start writing into file ---> " + str(OutputFile))
    file_stream = open(OutputFile, "w")
    for i in range(sim_top.shape[1]):
        if i < len(Q_hashes):
            file_stream.write(Q_hashes[i] + ",")
        else:
            file_stream.write(X_hashes[i - len(Q_hashes)] + ",")
        for j in range(min(Num_candidates, sim_top.shape[0])):
            score = sim[sim_top[j, i], i]  # discretize to 3 digit int
            file_stream.write(X_hashes[sim_top[j, i]] + " " + "{:.3f}".format(score) + " ")
        file_stream.write("\n")
        file_stream.flush()
    file_stream.close()


def load_npy(f):
    # load the npy file f
    return np.load(f)


def load_hashes(f):
    # load the txt file, which contains a name per line
    file_stream = open(f, "r")
    hashes = [line[:-1] for line in file_stream.readlines()]
    return hashes


def main():
    parser = argparse.ArgumentParser(description='Create a prebuild file for EGT from features')
    parser.add_argument('--query_features', type=str, help='numpy file location of query features', required=True)
    parser.add_argument('--index_features', type=str, help='numpy file location of index features', required=True)
    parser.add_argument('--query_hashes', type=str, help='file location of query hashes')
    parser.add_argument('--index_hashes', type=str, help='file location of index hashes')
    parser.add_argument('--Do_QE', type=str, help='whether to do QE', default=False, required=True)
    parser.add_argument('--QE_topN', type=int, help='number of elemenets to QE with. Default=2', default=2)
    parser.add_argument('--Num_candidates', type=int, help='number of candidates to retrieve', required=True)
    parser.add_argument('--evaluate', type=str, help='whether to evaluate the result for prebuild file. Either roxford5k or rparis6k')
    parser.add_argument('--OutputFile', type=str, help='file location for prebuild file', required=True)
    args = parser.parse_args()

    # load all the required data
    Q_features = load_npy(args.query_features)
    X_features = load_npy(args.index_features)

    # if hash is not given, use 0-num_queries for queries and num_queries-end for indexes
    if args.query_hashes:
        Q_hashes = load_hashes(args.query_hashes)
    else:
        Q_hashes = [str(i) for i in range(Q_features.shape[1])]
    if args.index_hashes:
        X_hashes = load_hashes(args.index_hashes)
    else:
        X_hashes = [str(i+len(Q_hashes)) for i in range(X_features.shape[1])]

    if args.evaluate is not None:
        if args.evaluate not in ['roxford5k', 'rparis6k']:
            raise ValueError('Not a valid boolean string. Possible input: {roxford5k, rparis6k}')

    # check if QE needs to be done
    if args.Do_QE == "True":
        Do_QE = True
    elif args.Do_QE == "False":
        Do_QE = False
    else:
        raise ValueError('Not a valid boolean string. Possible input: {True, False}')
    #Do_QE = args.Do_QE
    QE_topN = args.QE_topN
    evaluate = args.evaluate
    Num_candidates = args.Num_candidates
    OutputFile = args.OutputFile

    # generate prebuild
    generate_prebuild(Q_features, X_features, Q_hashes, X_hashes, Do_QE, QE_topN, Num_candidates, evaluate, OutputFile)


if __name__ == "__main__":
    main()
    #Q = load_npy("/media/jason/28c9eee1-312e-47d0-88ce-572813ebd6f1/graph/to_commit_embed_egt2/gae-pytorch-with-batch/org_query.npy") #change this to sys arg 1
    #X = load_npy("/media/jason/28c9eee1-312e-47d0-88ce-572813ebd6f1/graph/to_commit_embed_egt2/gae-pytorch-with-batch/org_index.npy") #change this to sys arg 2
    #Q_hashes = load_hashes("query_hashes.txt") #change this to sys arg 3
    #I_hashes = load_hashes("index_hashes.txt") #change this to sys arg 4
    #generate_prebuild(Q, X, Q_hashes, I_hashes, DO_QE=True, QE_topN=2, Num_candidates=100, OutputFile="prebuild_from_python.txt")

