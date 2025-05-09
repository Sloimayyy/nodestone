package com.sloimay.nodestonecore.simulation.backends.gpubackend.shader


class ShaderPreproc {

    companion object {

        fun preprocess(source: String, replacements: HashMap<String, String>): String {
            var newSource = source
            for ((k, v) in replacements) {
                newSource = newSource.replace("\"#$k\"", v)
            }
            return newSource
        }

    }

}

