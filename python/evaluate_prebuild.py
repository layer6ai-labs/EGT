import numpy as np
from revop import *
import sys
import argparse


DATA_PATH = '../data/evaluation/data/'

def read_file(prebuild, skip):
    f = open(prebuild, "r")
    lines = [line[:-1] for line in f.readlines()]
    lines = lines[skip:]
    f.close()
    return lines


def get_order(index_hashes):
    name2order = {}
    f = open(index_hashes, "r")
    hashes = [line[:-1] for line in f.readlines()]
    f.close()
    name2order = {}
    for i in range(len(hashes)):
        name2order[hashes[i]] = i
    return name2order


def evaluate_prebuild(prebuild, index_hashes, evaluate, num_query, num_score, skip):
    # evaluating the prebuild file
    # the format of the prebuild file should be something like:
    # query1, index score1 score2 ... index score1 score2
    # query2, index score1 score2 ... index score1 score2
    # ...

    # initialize the gnd for evaluation
    from revop import init_revop, eval_revop
    cfg = init_revop(evaluate, "../data/evaluation/data/")

    lines = read_file(prebuild, skip)
    name2order = get_order(index_hashes)
    
    rankings = []
    for i in range(num_query):
        parts = lines[i].split(",")
        q = parts[0]
        cands = parts[1].strip().split(" ")
        order = []
        for j in range(0, len(cands), num_score+1): # working for 0-x scores
            c = cands[j]
            order.append(name2order[c])
        rankings.append(order)
    
    rankings = np.array(rankings)
    rankings = np.reshape(rankings, (rankings.shape[0], rankings.shape[1]))
    rankings = rankings.T
    revop_map, revop_mapM = eval_revop(rankings)
    print("Map H: {:.2f}, M: {:.2f}".format(revop_map, revop_mapM))


def main():
    parser = argparse.ArgumentParser(description='Evaluate the revop of a given prebuild file')
    parser.add_argument('--f', type=str, help='prebuild file to be evaluated', required=True)
    parser.add_argument('--index_hashes', type=str, help='file location of index hashes', required=True)
    parser.add_argument('--num_query', type=int, help='Number of queries', required=True)
    parser.add_argument('--num_score', type=int, help='number of scores per pair', default=2, required=True)
    parser.add_argument('--evaluate', type=str, help='evaluate the result for prebuild file. Either roxford5k or rparis6k', required=True)
    parser.add_argument('--skip', type=int, help='number of header lines to skip', default=0)
    args = parser.parse_args()

    if args.evaluate is not None:
        if args.evaluate not in ['roxford5k', 'rparis6k']:
            raise ValueError('Not a valid boolean string. Possible input: {roxford5k, rparis6k}')
    
    if args.num_score < 0:
        raise ValueError('Not a valid boolean string. Possible input: num_score >= 1')

    f = args.f
    num_query = args.num_query
    num_score = args.num_score
    evaluate = args.evaluate
    skip = args.skip
    index_hashes = args.index_hashes
    evaluate_prebuild(f, index_hashes, evaluate, num_query, num_score, skip)

if __name__ == "__main__":
    main()
