package org.phlo.audio;

import org.junit.*;

public class TestFunctions {
	public float resultFloat;
	public double resultDouble;

	@Test
	public void testSincDouble() {
		double x = Double.MIN_VALUE;
		Assert.assertEquals(Functions.sinc(x), 1.0, 0.0);
		while (x < 1.0) {
			Assert.assertEquals("x=" + x, Taylor.sinc(x), Functions.sinc(x), 1e-15);
			x = x * 1.1 + Math.ulp(x);
		}
	}

	@Test
	public void testSincFloat() {
		float x = Float.MIN_VALUE;
		Assert.assertEquals(Functions.sinc(x), 1.0f, 0.0);
		while (x < 1.0f) {
			Assert.assertEquals("x=" + x, (float)Taylor.sinc(x), Functions.sinc(x), 1e-7f);
			x = x * 1.1f + Math.ulp(x);
		}
	}
	
	private static final int TestSincPerformanceN = 100000;

	@Test
	public void testSincFloatPerformance() {
		double sincOverhead = Float.POSITIVE_INFINITY;
		double sincWithOverhead = Float.POSITIVE_INFINITY;
		
		for(int n=0; n < 10; ++n) {
			resultDouble = 0;

			long startNanos = System.nanoTime();
			for(int i=2; i <= (TestSincPerformanceN + 1); ++i) {
				resultFloat += 1.0f / (float)i;
			}
			long overheadNanos = System.nanoTime();
			for(int i=2; i <= (TestSincPerformanceN + 1); ++i) {
				resultFloat += Functions.sinc(1.0f / (float)i);
			}
			long endNanos = System.nanoTime();
			
			sincOverhead = Math.min(sincOverhead, 1e-9 * (double)(overheadNanos - startNanos) / (double)TestSincPerformanceN);
			sincWithOverhead = Math.min(sincWithOverhead, 1e-9 * (double)(endNanos - overheadNanos) / (double)TestSincPerformanceN);
		}
		
		System.out.println("sinc(float x) takes " + (sincWithOverhead - sincOverhead) + " seconds (accounted for overhead of " + sincOverhead + " seconds)");
	}
	
	@Test
	public void testSincDoublePerformance() {
		double sincOverhead = Float.POSITIVE_INFINITY;
		double sincWithOverhead = Float.POSITIVE_INFINITY;
		
		for(int n=0; n < 10; ++n) {
			resultDouble = 0;

			long startNanos = System.nanoTime();
			for(int i=2; i <= (TestSincPerformanceN + 1); ++i) {
				resultDouble += 1.0 / (double)i;
			}
			long overheadNanos = System.nanoTime();
			for(int i=2; i <= (TestSincPerformanceN + 1); ++i) {
				resultDouble += Functions.sinc(1.0 / (double)i);
			}
			long endNanos = System.nanoTime();
			
			sincOverhead = Math.min(sincOverhead, 1e-9 * (double)(overheadNanos - startNanos) / (double)TestSincPerformanceN);
			sincWithOverhead = Math.min(sincWithOverhead, 1e-9 * (double)(endNanos - overheadNanos) / (double)TestSincPerformanceN);
		}
		
		System.out.println("sinc(double x) takes " + (sincWithOverhead - sincOverhead) + " seconds (accounted for overhead of " + sincOverhead + " seconds)");
	}
}
