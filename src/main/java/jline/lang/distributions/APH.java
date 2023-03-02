package jline.lang.distributions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jline.lang.JLineMatrix;
import jline.util.Pair;

@SuppressWarnings("unchecked")
public class APH extends MarkovianDistribution{

	public APH(List<Double> p, JLineMatrix generator) {
        super("APH", 2);
		
        this.setParam(1, "alpha", p);
        this.setParam(2, "T", generator);
	}

	public static APH fitMeanAndSCV(double mean, double scv) {
		//TODO(MEAN <= GlobalConstants.FineTol case)
		List<Double> list = new ArrayList<>();
		JLineMatrix matrix = new JLineMatrix(1,1);
		list.add(1.0);
		matrix.set(0, 0,  1);
		APH distribution = new APH(list, matrix);
		distribution.updateMeanAndSCV(mean, scv);
		distribution.immediate = false;
		return distribution;
	}

	private void updateMeanAndSCV(double mean, double scv) {
		double e1 = mean;
		double e2 = (1+scv)*(e1*e1);
		double[] args = new double[2];
		args[0] = e1;
		args[1] = e2;
		Pair<List<Double>, JLineMatrix> params= APHFrom2Moments(args);
		this.setParam(1, "alpha", params.getLeft());
		this.setParam(2, "T", params.getRight());

	}

		public static Pair<List<Double>, JLineMatrix> APHFrom2Moments(double[] moms) {
		double cv2 = moms[1] / Math.pow(moms[0], 2) - 1.0;
		double lambda = 1.0 / moms[0];
		int N = Math.max((int) Math.ceil(1.0 / cv2), 2);
		double p = 1.0 / (cv2 + 1.0 + (cv2 - 1.0) / (N - 1));
		JLineMatrix A = new JLineMatrix(N, N);
		for (int i = 0; i < N; i++) {
			A.set(i,i, -lambda * p * N);
			if (i < N - 1) {
				A.set(i, i + 1, -A.get(i, i));
			}
		}
		A.set(N - 1, N - 1, -lambda * N);
		System.out.println(N);
		List<Double> alpha = new ArrayList<>();
		for(int i = 0; i < N; i++) {
			alpha.add(0.0);
		}
		alpha.set(0,p);
		alpha.set(N - 1, 1.0 - p);
		return new Pair<>(alpha, A);
	}

	@Override
	public long getNumberOfPhases() {
		Map<Integer, JLineMatrix> PH = this.getRepres();
		return PH.get(0).numCols;
	}

	@Override
	public List<Double> sample(int n) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public double getMean() {
		return super.getMean();
	}

	@Override
	public double getRate() {
		return 1/getMean();
	}

	@Override
	public double getSCV() {
		return super.getSCV();
	}

	@Override
	public double getVar() {
		return this.getSCV()*Math.pow(this.getMean(), 2);
	}

	@Override
	public double getSkew() {
		return super.getSkew();
	}

	@Override
	public double evalCDF(double t) {		
		//Since currently no function to support calculating eigen values, thus calculating expm. This method is now not implemented.
		throw new RuntimeException("Not implemented");
	}

	@Override
	public double evalLST(double s) {
		return super.evalLST(s);
	}

	@Override
	public Map<Integer, JLineMatrix> getPH() {
		Map<Integer, JLineMatrix> res = new HashMap<Integer, JLineMatrix>();
		JLineMatrix T = getSubgenerator();
		
		JLineMatrix ones = new JLineMatrix(T.numCols,1,T.numCols);
		JLineMatrix expression1 = new JLineMatrix(0,0,0);
		JLineMatrix expression2 = new JLineMatrix(0,0,0);
		ones.fill(1.0);
		T.mult(ones, expression1);
		expression1.mult(this.getInitProb(), expression2);
		expression2.removeZeros(0);
		expression2.changeSign();
		
		res.put(0, T.clone());
		res.put(1, expression2);
		return res;
	}
	
	public JLineMatrix getSubgenerator() {
		return (JLineMatrix) this.getParam(2).getValue();
	}
	
	public JLineMatrix getInitProb() {
		List<Double> param1 = (List<Double>) this.getParam(1).getValue();
		JLineMatrix alpha = new JLineMatrix(1, param1.size(), param1.size());
		for(int i = 0; i < param1.size(); i++)
			alpha.set(0, i, param1.get(i));
		return alpha;
	}
}
