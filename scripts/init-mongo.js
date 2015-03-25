var indexSpec = {
    "user":[
        {index:{email:1},option:{name:"email",unique:true,background:true}}
    ],
    "following":[
        {index:{user_id:1,following_id:1},option:{name:"user_id_following_id",unique:true,background:true}}
    ],
    "follower":[
        {index:{user_id:1,follower_id:1},option:{name:"user_id_follower_id",unique:true,background:true}}
    ],
    "post":[
        {index:{user_id:1,created:1},option:{name:"user_id",background:true}},
        {index:{"comments._id":1},option:{name:"comment_id",sparse:true,unique:true,background:true}}
    ],
    "comment":[
        {index:{post_id:1,page:1},option:{name:"post_id_page",unique:true,background:true}},
        {index:{post_id:1,created:1},option:{name:"post_id_created",background:true}},
        {index:{"comments._id":1},option:{name:"comment_id",unique:true,background:true}}
    ],
    "feed":[
        {index:{modified:1},option:{name:"modified",expireAfterSeconds:86400,background:true}}
    ]
};

for (var colName in indexSpec)
{
    var col = db[colName];
    if(col.stats().ok != 1) {
        print("Create collection - " + colName);
        db.createCollection(colName);
        col = db[colName];
    }

    var indexInfo = db[colName].getIndexSpecs();
    for (var key in indexSpec[colName])
    {
        var spec = indexSpec[colName][key];
        var indexName = spec.option.name;
        var indexExists = false;
        for (var index in indexInfo)
        {
            if (indexInfo[index].name == indexName)
            {
                indexExists = true;
                break;
            }
        }
        if (!indexExists)
        {
            print("Create index:" + indexName + " for collection:" + colName);
            col.ensureIndex(spec.index, spec.option);
        }
    }
}
