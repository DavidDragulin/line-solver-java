package jline.lang.distributions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jline.lang.JLineMatrix;

@SuppressWarnings("unchecked")
public class APH extends MarkovianDistribution{

	public APH(List<Double> p, JLineMatrix generator) {
        super("APH", 2);
		
        this.setParam(1, "alpha", p);
        this.setParam(2, "t", generator);
	}

	public static APH fitMeanAndSCV(double mean, double scv) {
		List<Double> list = new ArrayList<>();
		JLineMatrix matrix = new JLineMatrix(1,1);
		list.add(1.0);
		matrix.set(0, 0,  1);
		APH distribution = new APH(list, matrix);
		distribution.updateMeanAndSCV(mean, scv);
		// TODO: finish function
		return null;
	}

	private void updateMeanAndSCV(double mean, double scv) {
		double e1 = mean;
		double e2 = (1+scv)*(e1*e1);

	}

	public static double[] APHFrom2Moments(double[] moms) {
		double cv2 = moms[1] / Math.pow(moms[0], 2) - 1.0;
		double lambda = 1.0 / moms[0];
		int N = Math.max((int) Math.ceil(1.0 / cv2), 2);
		double p = 1.0 / (cv2 + 1.0 + (cv2 - 1.0) / (N - 1));
		double[][] A = new double[N][N];
		double[] ans = new double[2];
		for (int i = 0; i < N; i++) {
			A[i][i] = -lambda * p * N;
			if (i < N - 1) {
				A[i][i + 1] = A[i][i];
				A[i + 1][i] = -A[i][i];
			}
		}
		A[N - 1][N - 1] = -lambda * N;
		double[] alpha = new double[N];
		alpha[0] = p;
		alpha[N - 1] = 1.0 - p;
		return null;
		// return new double[] {alpha, A};
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
