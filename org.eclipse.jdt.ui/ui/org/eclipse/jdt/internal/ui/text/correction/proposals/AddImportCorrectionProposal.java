/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction.proposals;

import java.util.List;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.IEditorPart;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.QualifiedTypeNameHistory;

import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;

import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;

public class AddImportCorrectionProposal extends ASTRewriteCorrectionProposal {

	private final String fTypeName;
	private final String fQualifierName;

	protected AddModuleRequiresCorrectionProposal fAdditionalProposal= null;

	public AddImportCorrectionProposal(String name, ICompilationUnit cu, int relevance, Image image, String qualifierName, String typeName, SimpleName node) {
		super(name, cu, ASTRewrite.create(node.getAST()), relevance, image);
		fTypeName= typeName;
		fQualifierName= qualifierName;
		fAdditionalProposal= getAdditionalChangeCorrectionProposal();
	}

	public String getQualifiedTypeName() {
		return fQualifierName + '.' + fTypeName;
	}

	public AddModuleRequiresCorrectionProposal getAdditionalProposal() {
		return fAdditionalProposal;
	}

	@Override
	protected void performChange(IEditorPart activeEditor, IDocument document) throws CoreException {
		super.performChange(activeEditor, document);
		rememberSelection();
	}

	private void rememberSelection() {
		QualifiedTypeNameHistory.remember(getQualifiedTypeName());
	}

	private AddModuleRequiresCorrectionProposal getAdditionalChangeCorrectionProposal() {
		ICompilationUnit cu= getCompilationUnit();
		AddModuleRequiresCorrectionProposal additionalChangeCorrectionProposal= null;
		IJavaProject currentJavaProject= cu.getJavaProject();
		if (currentJavaProject == null || !JavaModelUtil.is9OrHigher(currentJavaProject)) {
			return null;
		}
		IModuleDescription currentModuleDescription= null;
		try {
			currentModuleDescription= currentJavaProject.getModuleDescription();
		} catch (JavaModelException e1) {
			//DO NOTHING
		}
		if (currentModuleDescription == null) {
			return null;
		}
		ICompilationUnit currentModuleCompilationUnit= currentModuleDescription.getCompilationUnit();
		if (currentModuleCompilationUnit == null || !currentModuleCompilationUnit.exists()) {
			return null;
		}

		String qualifiedName= getQualifiedTypeName();
		List<IPackageFragment> packageFragments= AddModuleRequiresCorrectionProposal.getPackageFragmentsOfMatchingTypes(qualifiedName, IJavaSearchConstants.TYPE, currentJavaProject);
		IPackageFragment enclosingPackage= null;
		if (packageFragments.size() == 1) {
			enclosingPackage= packageFragments.get(0);
		}
		if (enclosingPackage != null) {
			IModuleDescription projectModule= null;
			if (enclosingPackage.isReadOnly()) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) enclosingPackage.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (root != null) {
					projectModule= root.getModuleDescription();
				}
			} else {
				IJavaProject project= enclosingPackage.getJavaProject();
				if (project != null && JavaModelUtil.is9OrHigher(project)) {
					try {
						projectModule= project.getModuleDescription();
					} catch (JavaModelException e) {
						//DO NOTHING
					}
				}
			}
			if (projectModule != null && projectModule.exists() && !projectModule.equals(currentModuleDescription)) {
				String moduleName= projectModule.getElementName();
				String[] args= { moduleName };
				final String changeName= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_add_requires_module_info, args);
				final String changeDescription= Messages.format(CorrectionMessages.UnresolvedElementsSubProcessor_add_requires_module_description, args);
				additionalChangeCorrectionProposal= new AddModuleRequiresCorrectionProposal(moduleName, changeName, changeDescription, currentModuleCompilationUnit, getRelevance());
			}
		}
		return additionalChangeCorrectionProposal;
	}

}
