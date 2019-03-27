import numpy as np
from revop import *
import sys
import argparse


DATA_PATH = '../data/evaluation/data/'

def read_file(prebuild):
    f = open(prebuild, "r")
    lines = [line[:-1] for line in f.readlines()]
    f.close()
    name2order = {}
    for i in range(len(lines)):
        q = lines[i].split(",")[0]
        name2order[q] = i
        sys.stdout.write("\rscanning file for the order: [" + str(i+1) + "/" + str(len(lines)) + "]")
        sys.stdout.flush()
    sys.stdout.write("\n")
    return lines, name2order


def evaluate_prebuild(prebuild, evaluate, num_query, num_score):
    # evaluating the prebuild file
    # the format of the prebuild file should be something like:
    # query1, index score1 score2 ... index score1 score2
    # query2, index score1 score2 ... index score1 score2
    # ...

    # initialize the gnd for evaluation
    from revop import init_revop, eval_revop
    cfg = init_revop(evaluate, "../data/evaluation/data/")

    lines, name2order = read_file(prebuild)
    
    rankings = []
    for i in range(num_query):
        parts = lines[i].split(",")
        q = parts[0]
        cands = parts[1].strip().split(" ")
        order = []
        for j in range(0, len(cands), num_score+1): # working for 0-x scores
            c = cands[j]
            order.append(name2order[c] - num_query)
        rankings.append(order)
    
    rankings = np.array(rankings)
    rankings = np.reshape(rankings, (rankings.shape[0], rankings.shape[1]))
    rankings = rankings.T
    revop_map = eval_revop(rankings)
    print("Map H: {:.2f}".format(revop_map))


def main():
    parser = argparse.ArgumentParser(description='Evaluate the revop of a given prebuild file')
    parser.add_argument('--f', type=str, help='prebuild file to be evaluated', required=True)
    parser.add_argument('--num_query', type=int, help='Number of queries', required=True)
    parser.add_argument('--num_score', type=int, help='number of scores per pair', default=2, required=True)
    parser.add_argument('--evaluate', type=str, help='evaluate the result for prebuild file. Either roxford5k or rparis6k', required=True)
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
    evaluate_prebuild(f, evaluate, num_query, num_score)

if __name__ == "__main__":
    main()
