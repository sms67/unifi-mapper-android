package com.unifimapper.org

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Local persistence: one JSON file per project inside filesDir/projects. */
object Store {

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "projects")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(ctx: Context): ArrayList<Project> {
        val out = ArrayList<Project>()
        val files = dir(ctx).listFiles() ?: return out
        for (f in files) {
            if (!f.name.endsWith(".json")) continue
            try {
                out.add(Project.fromJson(JSONObject(f.readText())))
            } catch (_: Exception) {
            }
        }
        out.sortByDescending { it.updatedAt }
        return out
    }

    fun save(ctx: Context, p: Project) {
        try {
            File(dir(ctx), p.id + ".json").writeText(p.toJson().toString())
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context, id: String): Project? {
        val f = File(dir(ctx), id + ".json")
        if (!f.exists()) return null
        return try {
            Project.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    fun delete(ctx: Context, id: String) {
        try {
            File(dir(ctx), id + ".json").delete()
        } catch (_: Exception) {
        }
    }
}
