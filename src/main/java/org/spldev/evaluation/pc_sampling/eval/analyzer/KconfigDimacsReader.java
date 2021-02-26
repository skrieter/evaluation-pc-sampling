/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evalaution of PC-Sampling.
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

import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.spldev.evaluation.pc_sampling.eval.Constants;
import org.spldev.formula.VariableMap;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.Clauses;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.expression.io.DimacsReader;

public class KconfigDimacsReader {

	private static final Charset charset = Charset.forName("UTF-8");

	public CNF load(String name) throws Exception {
		final Path kbuildPath = Constants.kbuildOutput.resolve(name).toAbsolutePath();
		final Path featureFile = kbuildPath.resolve(name + ".features");
		final Path modelFile = kbuildPath.resolve("model.dimacs");

		final Set<String> featureNames = Files.lines(featureFile, charset).filter(line -> !line.isEmpty())
				.collect(Collectors.toSet());
		final String source = new String(Files.readAllBytes(modelFile), charset);

		final DimacsReader r = new DimacsReader();
		r.setReadingVariableDirectory(true);
		final CNF cnf = Clauses.convertToCNF(r.read(new StringReader(source)));

		final Set<String> dirtyVariables = cnf.getVariableMap() //
				.getNames().stream() //
				.filter(variable -> !featureNames.contains("CONFIG_" + variable)) //
				.distinct() //
				.collect(Collectors.toSet());
		final CNF slicedCNF = Clauses.slice(cnf, dirtyVariables);

		final VariableMap slicedVariables = slicedCNF.getVariableMap();
		final VariableMap newVariables = new VariableMap(featureNames);
		final ClauseList newClauseList = new ClauseList();

		for (final LiteralList clause : slicedCNF.getClauses()) {
			final int[] oldLiterals = clause.getLiterals();
			final int[] newLiterals = new int[oldLiterals.length];
			for (int i = 0; i < oldLiterals.length; i++) {
				final int literal = oldLiterals[i];
				final int var = newVariables.getVariable("CONFIG_" + slicedVariables.getName(literal)).get();
				newLiterals[i] = literal > 0 ? var : -var;
			}
			newClauseList.add(new LiteralList(newLiterals));
		}

		return new CNF(newVariables, newClauseList);
	}

}
