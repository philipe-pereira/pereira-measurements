package br.com.pereiraeng.measurements.manegement;

import br.com.pereiraeng.swing.longtask.LongTaskManager;

public interface MedReader {

	public float[][] getLast(LongTaskManager<float[][]> ltm);

	public float[][] getPast(LongTaskManager<float[][]> ltm, int minute);
}
