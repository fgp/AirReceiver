package org.phlo.audio;

public final class Taylor {
	public interface Coefficients {
		public double coefficient(int power);
	}
	
	public static double taylor(double x, Coefficients source) {
		if ((x >= 1.0) || (x <= -1.0))
			throw new IllegalArgumentException("x must be strictly larger than -1.0 and strictly smaller than 1.0");
		
		double result = 0.0;
		double xnPrevious = 0.0;
		double xn = 1.0;
		int n = 0;
		while (xn != xnPrevious) {
			result += source.coefficient(n) * xn;
			n += 1;
			xnPrevious = xn;
			xn *= x;
		}
		
		return result;
	}
	
	public static final Coefficients SincCoefficients = new Coefficients() {
		@Override
		public double coefficient(int power) {
			if ((power % 2) != 0)
				return 0.0;
			
			double coeff = ((power/2) % 2 == 0) ? 1.0 : -1.0;
			for(int i=1; i <= (power + 1); ++i)
				coeff /= (double)i;
			
			return coeff;
		}
	};
	
	public static double sinc(double x) {
		return taylor(x, SincCoefficients);
	}
}
