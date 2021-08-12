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
package org.spldev.evaluation.pc_sampling.eval.analyzer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.spldev.evaluation.pc_sampling.eval.Constants;
import org.spldev.formula.clauses.CNF;

public class PresenceConditionList extends ArrayList<PresenceCondition> implements Serializable {

	private static final long serialVersionUID = 4377594672333226651L;

	private final CNF formula;

	private ArrayList<String> pcNames;

	public PresenceConditionList(List<PresenceCondition> list, CNF formula) {
		super(list);
		this.formula = formula;
	}

	public CNF getFormula() {
		return formula;
	}

	public static PresenceConditionList readPCList(String systemName, String fileName) throws IOException {
		try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(Constants.presenceConditionListOutput
				.resolve(systemName).resolve(fileName + "." + Constants.pcFileExtension).toString()))) {
			return (PresenceConditionList) in.readObject();
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void writePCList(PresenceConditionList pcList, String systemName, String fileName) {
		try {
			final Path filePath = Constants.presenceConditionListOutput.resolve(systemName)
					.resolve(fileName + "." + Constants.pcFileExtension);
			Files.deleteIfExists(filePath);
			Files.createDirectories(Constants.presenceConditionListOutput);
			try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath.toString()))) {
				out.writeObject(pcList);
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<String> getPCNames() {
		return pcNames;
	}

	public void setPCNames(ArrayList<String> pcNames) {
		this.pcNames = pcNames;
	}

}
