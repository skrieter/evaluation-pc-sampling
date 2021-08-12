/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evaluation of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.pc_sampling;

import java.util.Arrays;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.pc_sampling.eval.Extractor;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.logging.Logger;

public class PCExtractor extends Evaluator {

	protected CSVWriter extractionWriter;

	@Override
	public String getId() {
		return "eval-pc-extractor";
	}
	
	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		extractionWriter = addCSVWriter("extraction.csv",
				Arrays.asList("ID", "Mode", "Iteration", "Time", "Size", "Error"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.incTabLevel();

			final int systemIndexEnd = config.systemNames.size();
			for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
				logSystem();
				tabFormatter.incTabLevel();
				final String systemName = config.systemNames.get(systemIndex);

				// Extract PCs
				try {
					final Extractor pcfmProcessor = new Extractor(systemName);
					evalExtract(pcfmProcessor);
				} catch (final Exception e) {
					Logger.logError(e);
				}
				tabFormatter.decTabLevel();
			}
			tabFormatter.setTabLevel(0);
			Logger.logInfo("Finished");
		} else {
			Logger.logInfo("Nothing to do");
		}
	}

	private void evalExtract(final Extractor pcProcessor) {
		for (int i = 0; i < config.systemIterations.getValue(); i++) {
			extractionWriter.createNewLine();
			try {
				extractionWriter.addValue(config.systemIDs.get(systemIndex));
				extractionWriter.addValue("extract");
				extractionWriter.addValue(i);

				final long localTime = System.nanoTime();
				final boolean extracted = pcProcessor.extract();
				final long timeNeeded = System.nanoTime() - localTime;

				extractionWriter.addValue(timeNeeded);
				extractionWriter.addValue(0);
				extractionWriter.addValue(!extracted);

				Logger.logInfo("extract -> " + Double.toString((timeNeeded / 1_000_000) / 1_000.0));
			} catch (final Exception e) {
				extractionWriter.removeLastLine();
				e.printStackTrace();
			} finally {
				extractionWriter.flush();
			}
		}
	}

}
